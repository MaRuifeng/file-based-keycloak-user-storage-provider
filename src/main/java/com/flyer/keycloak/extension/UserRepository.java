package com.flyer.keycloak.extension;

import java.util.List;

/**
 * Low capacity user repository interface
 *
 * @author Ruifeng Ma
 * @since 2019-May-25
 */

public interface UserRepository {

    // basic CRUD operations
    void insertUser(User user);
    User getUser(String username);
    void updateUser(User user);
    void removeUser(String username);

    // complex queries
    int getUserCount();
    List<User> getAllUsers();
    List<User> findUserByKeyword(String keyword);
}
