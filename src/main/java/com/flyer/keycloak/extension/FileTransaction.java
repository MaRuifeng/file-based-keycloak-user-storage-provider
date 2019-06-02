package com.flyer.keycloak.extension;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.models.AbstractKeycloakTransaction;

import java.io.IOException;

/**
 * Custom Keycloak transaction for file based user repository
 *
 * @author Ruifeng Ma
 * @since 2019-May-25
 */

@JBossLog
public class FileTransaction extends AbstractKeycloakTransaction {

    private final FileUserRepository userRepository;
    private final User user;

    public FileTransaction(FileUserRepository userRepository, User user) {
        this.userRepository = userRepository;
        this.user = user;
    }

    @Override
    protected void commitImpl() {
        log.infov("Updating user to external repository in a transaction.");
        user.setPassword(user.getUsername()); // surely this needs to be more securely handled
        log.infov("User to be updated into the repository: {0}", user.toString());
        userRepository.updateUser(user);
        try {
            userRepository.persistUserDataToFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void rollbackImpl() {
        log.infov("Rolling back data change to external user repository ...");
    }
}