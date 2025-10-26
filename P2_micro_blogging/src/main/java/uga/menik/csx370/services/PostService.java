/**
Copyright (c) 2024 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles creating posts and persisting related hashtag data.
 */
@Service
public class PostService {

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("(^|\\s)#([\\p{Alnum}_]+)");

    private final DataSource dataSource;

    @Autowired
    public PostService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Creates a post for the provided user and stores any hashtags that appear in
     * the content.
     *
     * @param userId  the id of the user creating the post
     * @param content the raw post content
     * @return the generated post id
     */
    public long createPost(String userId, String content) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required to create a post.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Post content cannot be empty.");
        }

        long userIdValue;
        try {
            userIdValue = Long.parseLong(userId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("User id must be numeric.", ex);
        }

        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                long postId = insertPost(conn, userIdValue, content);
                Set<String> hashtags = extractHashtags(content);
                for (String tag : hashtags) {
                    long hashtagId = upsertHashtag(conn, tag);
                    linkPostToHashtag(conn, postId, hashtagId);
                }
                conn.commit();
                return postId;
            } catch (SQLException ex) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                throw new RuntimeException("Failed to create post.", ex);
            } finally {
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException resetEx) {
                    throw new RuntimeException("Failed to reset connection after creating post.", resetEx);
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to create post.", ex);
        }
    }

    private long insertPost(Connection conn, long userId, String content) throws SQLException {
        final String sql = "insert into posts (userId, content) values (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, userId);
            pstmt.setString(2, content);
            int affected = pstmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Inserting post failed, no rows affected.");
            }
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
                throw new SQLException("Inserting post failed, no generated key obtained.");
            }
        }
    }

    private long upsertHashtag(Connection conn, String tag) throws SQLException {
        final String sql = """
                insert into hashtags (tag) values (?)
                on duplicate key update hashtagId = last_insert_id(hashtagId)
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, tag);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        // Fallback in the unlikely event generated keys are unavailable.
        final String selectSql = "select hashtagId from hashtags where tag = ?";
        try (PreparedStatement selectPstmt = conn.prepareStatement(selectSql)) {
            selectPstmt.setString(1, tag);
            try (ResultSet rs = selectPstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("hashtagId");
                }
            }
        }
        throw new SQLException("Failed to resolve hashtag id for tag: " + tag);
    }

    private void linkPostToHashtag(Connection conn, long postId, long hashtagId) throws SQLException {
        final String sql = "insert into post_hashtags (post_id, hashtag_id) values (?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, postId);
            pstmt.setLong(2, hashtagId);
            pstmt.executeUpdate();
        }
    }

    private Set<String> extractHashtags(String content) {
        Set<String> tags = new LinkedHashSet<>();
        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        while (matcher.find()) {
            String rawTag = matcher.group(2);
            if (rawTag != null && !rawTag.isBlank()) {
                tags.add(rawTag.toLowerCase());
            }
        }
        return tags;
    }
}
