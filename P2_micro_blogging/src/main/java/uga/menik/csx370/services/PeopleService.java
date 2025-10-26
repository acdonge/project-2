package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uga.menik.csx370.models.FollowableUser;

/**
 * Service: handles logic for the People page.
 * Shows who can be followed and manages follow/unfollow actions.
 * Uses raw JDBC for transparency and control.
 */
@Service
public class PeopleService {

    private final DataSource pool;

    @Autowired
    public PeopleService(DataSource pool) {
        this.pool = pool;
    }

    /**
     * Retrieves a list of all users except the current one.
     * Adds each user's most recent post timestamp for the UI display.
     */
    public List<FollowableUser> getFollowableUsers(String currentUserId) {
        List<FollowableUser> results = new ArrayList<>();

        final String q =
            "SELECT u.userId, u.username, u.firstName, u.lastName, " +
            "  DATE_FORMAT((SELECT MAX(p.created_at) FROM post p WHERE p.userId = u.userId), " +
            "  '%b %d, %Y, %h:%i %p') AS last_post_time " +
            "FROM user u WHERE u.userId <> ? ORDER BY u.username ASC";

        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(q)) {

            ps.setString(1, currentUserId);

            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    results.add(new FollowableUser(
                        r.getString("userId"),
                        r.getString("username"),
                        r.getString("firstName"),
                        r.getString("lastName"),
                        r.getString("last_post_time"),
                        false // default follow state
                    ));
                }
            }

        } catch (SQLException sqle) {
            System.err.println("[PeopleService] DB error while loading followable users:");
            sqle.printStackTrace();
        }

        return results;
    }

    /**
     * Inserts a follow relation. Duplicate entries are ignored.
     */
    public void followUser(String followerId, String followeeId) {
        final String add =
            "INSERT IGNORE INTO follows (follower_id, followee_id, created_at) VALUES (?, ?, NOW())";

        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(add)) {

            ps.setString(1, followerId);
            ps.setString(2, followeeId);
            ps.executeUpdate();

        } catch (SQLException sqle) {
            System.err.println("[PeopleService] DB error while adding follow:");
            sqle.printStackTrace();
        }
    }

    /**
     * Deletes an existing follow link.
     */
    public void unfollowUser(String followerId, String followeeId) {
        final String del =
            "DELETE FROM follows WHERE follower_id = ? AND followee_id = ?";

        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(del)) {

            ps.setString(1, followerId);
            ps.setString(2, followeeId);
            ps.executeUpdate();

        } catch (SQLException sqle) {
            System.err.println("[PeopleService] DB error while removing follow:");
            sqle.printStackTrace();
        }
    }
}
