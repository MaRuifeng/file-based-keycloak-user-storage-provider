package com.flyer.keycloak.extension;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Keycloak file based user storage provider
 *
 * @author Ruifeng Ma
 * @since 2019-May-25
 */

@JBossLog
public class FileUserStorageProvider implements
        UserStorageProvider,
        UserLookupProvider,
        UserQueryProvider,
        UserRegistrationProvider {
    private final KeycloakSession session;
    private final ComponentModel model; // represents how the provider is enabled and configured within a specific realm
    private final FileUserRepository userRepository;
    private final Map<String, UserModel> loadedUsers;

    public FileUserStorageProvider(KeycloakSession session, ComponentModel model, FileUserRepository userRepository) {
        this.session = session;
        this.model = model;
        this.userRepository = userRepository;
        this.loadedUsers = new HashMap<>();
    }

    @Override
    public void close() {
        log.infov("End of transaction.");
    }

    /* UserLookupProvider interface implementation (Start) */

    @Override
    public UserModel getUserById(String id, RealmModel realm) {
        log.infov("Looking up user via id: id={0} realm={1}", id, realm.getId());
        StorageId storageId = new StorageId(id);
        String externalId = storageId.getExternalId(); // user id format - "f:" + component id + ":" + username
        return getUserByUsername(externalId, realm);
    }

    @Override
    public UserModel getUserByUsername(String username, RealmModel realm) {
        log.infov("Looking up user via username: id={0} realm={1}", username, realm.getId());
        UserModel adapter = loadedUsers.get(username);
        if (adapter == null) {
            session.userFederatedStorage().getStoredUsers(realm, 0, Integer.MAX_VALUE).stream().forEach(user -> {
                log.infov("User in federated storage: {0}", user);
                log.infov("Attributes: {0}", session.userFederatedStorage().getAttributes(realm, user));
            });
            User user = userRepository.getUser(username);
            if (user == null) return null;
            adapter = createAdapter(realm, user);
            loadedUsers.put(username, adapter);
        }
        return adapter;
    }

    @Override
    public UserModel getUserByEmail(String email, RealmModel realm) {
        log.infov("Looking up user via email: email={0} realm={1}", email, realm.getId());
        return getUserByUsername(email, realm);
    }
    /* UserLookupProvider interface implementation (End) */

    /* UserQueryProvider interface implementation (Start) */
    @Override
    public int getUsersCount(RealmModel realm) {
        log.infov("Getting user count ...");
        return userRepository.getUserCount();
    }

    @Override
    public List<UserModel> getUsers(RealmModel realm) {
        log.infov("Getting all users ...");
        return getUsers(realm, 0, Integer.MAX_VALUE);
    }

    @Override
    public List<UserModel> getUsers(RealmModel realm, int firstResult, int maxResults) {
        List<UserModel> userModelList = new LinkedList<>();
        int i = 0;
        for (User user : userRepository.getAllUsers()) {
            if (i++ < firstResult) continue;
            UserModel userModel = getUserByUsername(user.getUsername(), realm);
            userModelList.add(userModel);
            if (userModelList.size() >= maxResults) break;
        }
        return userModelList;
    }

    @Override
    public List<UserModel> searchForUser(String search, RealmModel realm) {
        log.infov("Searching for user: search={0} realm={1}", search, realm.getId());
        return userRepository.findUserByKeyword(search).stream()
                .map(user -> getUserByUsername(user.getUsername(), realm))
                .collect(Collectors.toList());
    }

    @Override
    public List<UserModel> searchForUser(String search, RealmModel realm, int firstResult, int maxResults) {
        return searchForUser(search, realm);
    }

    @Override
    public List<UserModel> searchForUser(Map<String, String> params, RealmModel realm) {
        log.infov("Searching for user: params={0} realm={1}", params.toString(), realm.getId());
        if (params.isEmpty()) return getUsers(realm);
        String usernameParam = params.get("username");
        if (usernameParam == null) return Collections.EMPTY_LIST;
        return searchForUser(usernameParam, realm);
    }

    @Override
    public List<UserModel> searchForUser(Map<String, String> params, RealmModel realm, int firstResult, int maxResults) {
        return searchForUser(params, realm);
    }

    @Override
    public List<UserModel> getGroupMembers(RealmModel realm, GroupModel group, int firstResult, int maxResults) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<UserModel> getGroupMembers(RealmModel realm, GroupModel group) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<UserModel> searchForUserByUserAttribute(String attrName, String attrValue, RealmModel realm) {
        return Collections.EMPTY_LIST;
    }

    /* UserQueryProvider interface implementation (End) */

    /* UserRegistrationProvider interface implementation (Start) */
    @Override
    public UserModel addUser(RealmModel realm, String username) {
        log.infov("Adding new user to file repository: username={0}", username);
        User user = new User();
        user.setUsername(username);
        userRepository.insertUser(user);
        UserModel userModel = createAdapter(realm, user);
        return userModel;
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        log.infov("Removing user: user={0}", user.getUsername());
        try {
            userRepository.removeUser(user.getUsername());
            userRepository.persistUserDataToFile();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /* UserRegistrationProvider interface implementation (End) */

    private UserModel createAdapter(RealmModel realm, User user) {
        return new AbstractUserAdapterFederatedStorage(session, realm, model) { // anonymous class inheriting the abstract parent
            @Override
            public String getUsername() {
                log.infov("[Keycloak UserModel Adapter] Getting username ...");
                return user.getUsername();
            }

            @Override
            public void setUsername(String username) {
                log.infov("[Keycloak UserModel Adapter] Setting username ...");
                user.setUsername(username);
            }

            @Override
            public void setAttribute(String name, List<String> values) {
                log.infov("[Keycloak UserModel Adapter] Setting attribute {0} with values {1}", name, values);
                switch (name) {
                    case "favouriteLine":
                        // purposely skip attribute setting to federatedStorage so it won't
                        // get captured by Keycloak's local DB and shown via the Admin console

                        FileTransaction transaction = new FileTransaction(userRepository, user);
                        ensureTransactionStarted(transaction);
                        user.setFavouriteLine(values.get(0));

                        break;
                    default:
                        getFederatedStorage().setAttribute(realm, this.getId(), name, values);
                }
            }
        };
    }

    private void ensureTransactionStarted(FileTransaction transaction) {
        if (transaction.getState() == FileTransaction.TransactionState.NOT_STARTED) {
            log.infov("Enlisting user repository transaction ...");
            session.getTransactionManager().enlistAfterCompletion(transaction);
        }
    }
}
