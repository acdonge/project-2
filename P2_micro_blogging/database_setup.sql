-- Create the database
CREATE DATABASE IF NOT EXISTS csx370_mb_platform;
USE csx370_mb_platform;

-- User table
CREATE TABLE IF NOT EXISTS user (
    userId INT AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    firstName VARCHAR(255) NOT NULL,
    lastName VARCHAR(255) NOT NULL,
    PRIMARY KEY (userId),
    UNIQUE (username),
    CONSTRAINT userName_min_length CHECK (CHAR_LENGTH(TRIM(username)) >= 2),
    CONSTRAINT firstName_min_length CHECK (CHAR_LENGTH(TRIM(firstName)) >= 2),
    CONSTRAINT lastName_min_length CHECK (CHAR_LENGTH(TRIM(lastName)) >= 2)
) ENGINE=InnoDB;

-- Posts
CREATE TABLE IF NOT EXISTS posts (
    postId INT AUTO_INCREMENT,
    userId INT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (postId),
    FOREIGN KEY (userId) REFERENCES user(userId) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Follows
CREATE TABLE IF NOT EXISTS follows (
    follower_id INT NOT NULL,
    followee_id INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, followee_id),
    FOREIGN KEY (follower_id) REFERENCES user(userId) ON DELETE CASCADE,
    FOREIGN KEY (followee_id) REFERENCES user(userId) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Likes
CREATE TABLE IF NOT EXISTS likes (
    user_id INT NOT NULL,
    post_id INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, post_id),
    FOREIGN KEY (user_id) REFERENCES user(userId) ON DELETE CASCADE,
    FOREIGN KEY (post_id) REFERENCES posts(postId) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Bookmarks
CREATE TABLE IF NOT EXISTS bookmarks (
    user_id INT NOT NULL,
    post_id INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, post_id),
    FOREIGN KEY (user_id) REFERENCES user(userId) ON DELETE CASCADE,
    FOREIGN KEY (post_id) REFERENCES posts(postId) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Comments
CREATE TABLE IF NOT EXISTS comments (
    commentId INT AUTO_INCREMENT,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (commentId),
    FOREIGN KEY (post_id) REFERENCES posts(postId) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES user(userId) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Hashtags
CREATE TABLE IF NOT EXISTS hashtags (
    hashtagId INT AUTO_INCREMENT,
    tag VARCHAR(100) NOT NULL,
    PRIMARY KEY (hashtagId),
    UNIQUE (tag)
) ENGINE=InnoDB;

-- Post-Hashtag mapping
CREATE TABLE IF NOT EXISTS post_hashtags (
    post_id INT NOT NULL,
    hashtag_id INT NOT NULL,
    PRIMARY KEY (post_id, hashtag_id),
    FOREIGN KEY (post_id) REFERENCES posts(postId) ON DELETE CASCADE,
    FOREIGN KEY (hashtag_id) REFERENCES hashtags(hashtagId) ON DELETE CASCADE
) ENGINE=InnoDB;