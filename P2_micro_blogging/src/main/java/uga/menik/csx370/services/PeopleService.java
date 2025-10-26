/**
Copyright (c) 2024 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uga.menik.csx370.models.FollowableUser;

/**
 * This service contains people related functions.
 */
@Service
public class PeopleService {

    /**
     * Enables database access for people related queries.
     */
    private final DataSource dataSource;
    private static final String NO_ACTIVITY_PLACEHOLDER = "No recent activity";
    private static final DateTimeFormatter LAST_ACTIVE_FORMATTER = DateTimeFormatter.ofPattern(
            "MMM dd, yyyy, hh:mm a", Locale.US);

    /**
     * Inject the configured datasource so we can access the database.
     */
    @Autowired
    public PeopleService(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    /**
     * Retrieves all users in the system, augmenting each record with follow status
     * from the perspective of the current user and their last post timestamp.
     * The logged-in user is also included so the UI can indicate the current profile.
     */
    public List<FollowableUser> getFollowableUsers(String currentUserId) {
        List<FollowableUser> followableUsers = new ArrayList<>();

        Long currentUserLongId = toNullableLong(currentUserId);
        String currentUserStringId = currentUserLongId == null ? null : Long.toString(currentUserLongId);

        final String sql = """
                select u.userId,
                       u.firstName,
                       u.lastName,
                       coalesce(max(case when f.follower_id is null then 0 else 1 end), 0) as isFollowed,
                       max(p.created_at) as lastPost
                from user u
                left join follows f
                    on f.followee_id = u.userId
                    and f.follower_id = ?
                left join posts p
                    on p.userId = u.userId
                group by u.userId, u.firstName, u.lastName
                order by u.firstName, u.lastName
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (currentUserLongId == null) {
                pstmt.setNull(1, Types.BIGINT);
            } else {
                pstmt.setLong(1, currentUserLongId);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String userId = rs.getString("userId");
                    String firstName = rs.getString("firstName");
                    String lastName = rs.getString("lastName");
                    boolean isFollowed = rs.getInt("isFollowed") > 0;
                    Timestamp lastPostTime = rs.getTimestamp("lastPost");
                    String lastActiveDate = formatTimestamp(lastPostTime);
                    boolean isSelf = currentUserStringId != null && currentUserStringId.equals(userId);

                    followableUsers.add(new FollowableUser(userId, firstName, lastName,
                            isFollowed, lastActiveDate, isSelf));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch followable users from the database.", e);
        }

        return followableUsers;
    }

    /**
     * Adds a follow relationship between the logged-in user and the specified user.
     */
    public void followUser(String followerId, String followeeId) {
        long follower = parseRequiredUserId(followerId);
        long followee = parseRequiredUserId(followeeId);
        if (follower == followee) {
            throw new IllegalArgumentException("Users cannot follow themselves.");
        }

        final String sql = """
                insert into follows (follower_id, followee_id)
                values (?, ?)
                on duplicate key update created_at = created_at
                """;
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, follower);
            pstmt.setLong(2, followee);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to follow the user.", e);
        }
    }

    /**
     * Removes a follow relationship between the logged-in user and the specified user.
     */
    public void unfollowUser(String followerId, String followeeId) {
        long follower = parseRequiredUserId(followerId);
        long followee = parseRequiredUserId(followeeId);
        final String sql = "delete from follows where follower_id = ? and followee_id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, follower);
            pstmt.setLong(2, followee);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to unfollow the user.", e);
        }
    }

    private Long toNullableLong(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("User id must be numeric.", ex);
        }
    }

    private long parseRequiredUserId(String userId) {
        Long value = toNullableLong(userId);
        if (value == null) {
            throw new IllegalArgumentException("User id is required.");
        }
        return value;
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return NO_ACTIVITY_PLACEHOLDER;
        }
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return LAST_ACTIVE_FORMATTER.format(localDateTime);
    }

}
