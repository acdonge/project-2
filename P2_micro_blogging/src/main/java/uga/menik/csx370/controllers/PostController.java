/**
Copyright (c) 2024 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import uga.menik.csx370.models.ExpandedPost;
import uga.menik.csx370.models.User;
import uga.menik.csx370.models.Comment;
import uga.menik.csx370.services.UserService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Handles /post URL and its sub urls.
 */
@Controller
@RequestMapping("/post")
public class PostController {

    private final UserService userService;
    private final DataSource dataSource;

    /**
     * See notes in AuthInterceptor.java regarding how this works 
     * through dependency injection and inversion of control.
     */
    @Autowired
    public PostController(UserService userService, DataSource dataSource) {
        this.userService = userService;
        this.dataSource = dataSource;
    }

    
    
    /**
     * This function handles the /post/{postId} URL.
     * This handlers serves the web page for a specific post.
     * Note there is a path variable {postId}.
     * An example URL handled by this function looks like below:
     * http://localhost:8081/post/1
     * The above URL assigns 1 to postId.
     * 
     * See notes from HomeController.java regardig error URL parameter.
     */
    @GetMapping("/{postId}")
    public ModelAndView webpage(@PathVariable("postId") String postId,
            @RequestParam(name = "error", required = false) String error) {
        System.out.println("The user is attempting to view post with id: " + postId);
        // See notes on ModelAndView in BookmarksController.java.
        ModelAndView mv = new ModelAndView("posts_page");

        // Following line populates sample data.
        // You should replace it with actual data from the database.
        List<ExpandedPost> posts = new ArrayList<>();

        final String postSql = """
            SELECT postId, content, created_at, userId
            FROM posts
            WHERE postId = ?
        """;
    
        final String commentSql = """
            SELECT content, created_at, user_id
            FROM comments
            WHERE post_id = ?
            ORDER BY created_at ASC
        """;
    
        try (Connection conn = dataSource.getConnection();
             PreparedStatement postStmt = conn.prepareStatement(postSql)) {
    
            postStmt.setString(1, postId);
            try (ResultSet postRs = postStmt.executeQuery()) {
                if (postRs.next()) {
                    String content = postRs.getString("content");
                    String postDate = postRs.getTimestamp("created_at")
                                            .toLocalDateTime()
                                            .format(DateTimeFormatter.ofPattern("MMMM dd, yyyy, hh:mm a"));
    
                    String postUserId = postRs.getString("userId");
    
                    User postUser = new User(postUserId, "", "");
    
                    int likeCount = 0;
                    String likeSql = "SELECT COUNT(*) FROM likes WHERE post_id = ?";
                    try (PreparedStatement likeStmt = conn.prepareStatement(likeSql)) {
                        likeStmt.setString(1, postId);
                        try (ResultSet likeRs = likeStmt.executeQuery()) {
                            if (likeRs.next()) {
                                likeCount = likeRs.getInt(1);
                            }
                        }
                    }
    
                    int commentCount = 0;
                    try (PreparedStatement commentCountStmt = conn.prepareStatement("SELECT COUNT(*) FROM comments WHERE post_id = ?")) {
                        commentCountStmt.setString(1, postId);
                        try (ResultSet commentCountRs = commentCountStmt.executeQuery()) {
                            if (commentCountRs.next()) {
                                commentCount = commentCountRs.getInt(1);
                            } 
                        }
                    }
    
                    List<Comment> comments = new ArrayList<>();
                    try (PreparedStatement commentStmt = conn.prepareStatement(commentSql)) {
                        commentStmt.setString(1, postId);
                        try (ResultSet commentRs = commentStmt.executeQuery()) {
                            while (commentRs.next()) {
                                String commentContent = commentRs.getString("content");
                                String commentDate = commentRs.getTimestamp("created_at")
                                                              .toLocalDateTime()
                                                              .format(DateTimeFormatter.ofPattern("MMMM dd, yyyy, hh:mm a"));
                                String commentUserId = commentRs.getString("user_id");
                                User commentUser = new User(commentUserId, "", "");
                                comments.add(new Comment(postId, commentContent, commentDate, commentUser));
                            }
                        }
                    }
    
                    boolean isHearted = false;
                    boolean isBookmarked = false;
    
                    User currentUser = userService.getLoggedInUser();
                    if (currentUser != null) {
                        String currentUserId = currentUser.getUserId();
    
                        String heartSql = "SELECT COUNT(*) FROM likes WHERE post_id = ? AND user_id = ?";
                        try (PreparedStatement heartStmt = conn.prepareStatement(heartSql)) {
                            heartStmt.setString(1, postId);
                            heartStmt.setString(2, currentUserId);
                            try (ResultSet heartRs = heartStmt.executeQuery()) {
                                if (heartRs.next() && heartRs.getInt(1) > 0) {
                                    isHearted = true;
                                }
                            }
                        }
    
                        String bookmarkSql = "SELECT COUNT(*) FROM bookmarks WHERE post_id = ? AND user_id = ?";
                        try (PreparedStatement bookmarkStmt = conn.prepareStatement(bookmarkSql)) {
                            bookmarkStmt.setString(1, postId);
                            bookmarkStmt.setString(2, currentUserId);
                            try (ResultSet bookmarkRs = bookmarkStmt.executeQuery()) {
                                if (bookmarkRs.next() && bookmarkRs.getInt(1) > 0) {
                                    isBookmarked = true;
                                }
                            }
                        }
                    }
    
                    ExpandedPost expandedPost = new ExpandedPost(
                            postId,
                            content,
                            postDate,
                            postUser,
                            likeCount,
                            commentCount,
                            isHearted,
                            isBookmarked,
                            comments
                    );
    
                    posts.add(expandedPost);
                }
            }
    
        } catch (SQLException e) {
            e.printStackTrace();
            mv.addObject("errorMessage", error);
        }
    
        mv.addObject("posts", posts);
        return mv;

        // Enable the following line if you want to show no content message.
        // Do that if your content list is empty.
        // mv.addObject("isNoContent", true);
    }
        

    /**
     * Handles comments added on posts.
     * See comments on webpage function to see how path variables work here.
     * This function handles form posts.
     * See comments in HomeController.java regarding form submissions.
     */
    @PostMapping("/{postId}/comment")
    public String postComment(@PathVariable("postId") String postId,
            @RequestParam(name = "comment") String comment) {
        System.out.println("The user is attempting add a comment:");
        System.out.println("\tpostId: " + postId);
        System.out.println("\tcomment: " + comment);

        User currentUser = userService.getLoggedInUser();
        String userId = currentUser.getUserId();

        final String sqlInsert = "INSERT INTO comments(post_id, user_id, content, created_at) VALUES (?, ?, ?, NOW())";
        
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement pstmt = conn.prepareStatement(sqlInsert);
            pstmt.setString(1, postId);
            pstmt.setString(2, userId);
            pstmt.setString(3, comment);
            pstmt.executeUpdate();
            return "redirect:/post/" + postId;
        } catch (SQLException e) {
            e.printStackTrace();
            String message = URLEncoder.encode("Failed to post the comment. Please try again.",
                                            StandardCharsets.UTF_8);
            return "redirect:/post/" + postId + "?error=" + message;
        }
    }

    /**
     * Handles likes added on posts.
     * See comments on webpage function to see how path variables work here.
     * See comments in PeopleController.java in followUnfollowUser function regarding 
     * get type form submissions and how path variables work.
     */
    @GetMapping("/{postId}/heart/{isAdd}")
    public String addOrRemoveHeart(@PathVariable("postId") String postId,
            @PathVariable("isAdd") Boolean isAdd) {
        System.out.println("The user is attempting add or remove a heart:");
        System.out.println("\tpostId: " + postId);
        System.out.println("\tisAdd: " + isAdd);

        User currentUser = userService.getLoggedInUser();
        String userId = currentUser.getUserId();

        final String sqlInsert = "INSERT IGNORE INTO likes (user_id, post_id, created_at) VALUES (?, ?, NOW())";
        final String sqlDelete = "DELETE FROM likes WHERE user_id=? AND post_id=?";

        try (Connection conn = dataSource.getConnection()) {
            if (isAdd) {
                try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                    pstmt.setInt(1, Integer.parseInt(userId));
                    pstmt.setInt(2, Integer.parseInt(postId));
                    int rows = pstmt.executeUpdate();
                    System.out.println("Rows inserted: " + rows);
                }
            } else {
                try (PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
                    pstmt.setInt(1, Integer.parseInt(userId));
                    pstmt.setInt(2, Integer.parseInt(postId));
                    int rows = pstmt.executeUpdate();
                    System.out.println("Rows deleted: " + rows);
                }
            }
            return "redirect:/post/" + postId;
        } catch (SQLException e) {
            e.printStackTrace();
            String message = URLEncoder.encode("Failed to (un)like the post. Please try again.",
                                               StandardCharsets.UTF_8);
            return "redirect:/post/" + postId + "?error=" + message;
        }
    }

    /**
     * Handles bookmarking posts.
     * See comments on webpage function to see how path variables work here.
     * See comments in PeopleController.java in followUnfollowUser function regarding 
     * get type form submissions.
     */
    @GetMapping("/{postId}/bookmark/{isAdd}")
    public String addOrRemoveBookmark(@PathVariable("postId") String postId,
            @PathVariable("isAdd") Boolean isAdd) {
        System.out.println("The user is attempting add or remove a bookmark:");
        System.out.println("\tpostId: " + postId);
        System.out.println("\tisAdd: " + isAdd);

        // Redirect the user if the comment adding is a success.
        User currentUser = userService.getLoggedInUser();
        String userId = currentUser.getUserId();

        final String sqlInsert = "INSERT IGNORE INTO bookmarks(user_id, post_id, created_at) VALUES(?, ?, NOW())";
        final String sqlDelete = "DELETE FROM bookmarks WHERE user_id = ? AND post_id = ?";

        try (Connection conn = dataSource.getConnection()) {

            if (isAdd) {
                try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                    pstmt.setString(1, userId);
                    pstmt.setString(2, postId);
                    int rows = pstmt.executeUpdate();
                    System.out.println("Rows added: " + rows);
                }
            } else {
                try (PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
                    pstmt.setString(1, userId);
                    pstmt.setString(2, postId);
                    int rows = pstmt.executeUpdate();
                    System.out.println("Rows deleted: " + rows);
                }
            }
            return "redirect:/post/" + postId;
        } catch (SQLException e) {
            e.printStackTrace();
            String message = URLEncoder.encode("Failed to (un)bookmark the post. Please try again.",
                                                StandardCharsets.UTF_8);
            return "redirect:/post/" + postId + "?error=" + message;
        }
    }
}
