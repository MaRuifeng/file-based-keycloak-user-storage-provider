package com.flyer.keycloak.extension;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Custom user model
 *
 * When implemented in a relational database, username shall be the primary key
 *
 * @author Ruifeng Ma
 * @since 2019-May-25
 */

@Data
@NoArgsConstructor
@ToString

public class User {
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String favouriteLine; // custom attribute

    public User(String firstName, String lastName, String email, String favouriteLine) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.favouriteLine = favouriteLine;
        this.email = email;
        this.username = this.email.toLowerCase();
        this.password = this.username;
    }
}
