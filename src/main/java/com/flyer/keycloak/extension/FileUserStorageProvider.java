package com.flyer.keycloak.extension;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;
import org.keycloak.models.utils.UserModelDelegate;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.ImportedUserValidation;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

import java.io.IOException;
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
        CredentialInputValidator,
        UserQueryProvider,
        UserRegistrationProvider,
        ImportedUserValidation {
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

    /* UserLookupProvider interface implementation (Start) */
    @Override
    public void close() {
        log.infov("Persisting user data changes ...");
        try {
            userRepository.persistUserDataToFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.infov("Closing at the end of the transaction.");
    }

    @Override
    public UserModel getUserById(String id, RealmModel realm) {
        log.infov("Looking up user via: id={0} realm={1}", id, realm.getId());
        StorageId storageId = new StorageId(id);
        String externalId = storageId.getExternalId(); // user id format - "f:" + component id + ":" + username
        return getUserByUsername(externalId, realm);
    }

    @Override
    public UserModel getUserByUsername(String username, RealmModel realm) {
        log.infov("Looking up user via username: username={0} realm={1}", username, realm.getId());
        UserModel adapter = loadedUsers.get(username);
        if (adapter == null) {
            User user = userRepository.getUser(username);
            if (user != null) {
                adapter = createAdapter(realm, user);
                loadedUsers.put(username, adapter);
            }
        }
        return adapter;
    }

    @Override
    public UserModel getUserByEmail(String email, RealmModel realm) {
        log.infov("Looking up user via email: email={0} realm={1}", email, realm.getId());
        return getUserByUsername(email.replace("@flyer.com", ""), realm);
    }
    /* UserLookupProvider interface implementation (End) */

    /* CredentialInputValidator interface implementation (Start) */
    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        String password = userRepository.getUser(user.getUsername()).getPassword();
        log.infov("Checking authentication setup ...");
        return credentialType.equals(CredentialModel.PASSWORD) && password != null;
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return credentialType.equals(CredentialModel.PASSWORD);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) return false;

        UserCredentialModel cred = (UserCredentialModel) input;
        String password = userRepository.getUser(user.getUsername()).getPassword();

        if (password == null) return false;
        return password.equals(HashUtil.hashString(cred.getValue()));
    }
    /* CredentialInputValidator interface implementation (End) */

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
        user.setPassword(username);
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
        log.infov("Number of users in local storage: {0}", session.userLocalStorage().getUsersCount(realm));
        session.userLocalStorage().getUsers(realm, 0, Integer.MAX_VALUE)
                .stream().forEach(u -> log.infov("User in local storage: {0}", u.getUsername()));

        UserModel localUser = session.userLocalStorage().getUserByUsername(user.getUsername(), realm);

        if (localUser == null) {
            log.infov("Adding user to local storage: {0}", user.getUsername());
            localUser = session.userLocalStorage().addUser(realm, user.getUsername());
            localUser.setFederationLink(model.getId());
        } else {
            log.infov("Found user in local storage: {0}", localUser.getUsername());
            localUser.getAttributes().forEach((key, value) -> log.infov("Attribute - {0} : {1}", key, value));
        }

        return new UserModelDelegate(localUser) {
            @Override
            public void setAttribute(String name, List<String> values) {
                switch (name) {
                    case "favouriteLine":
                        // purposely skip attribute setting to userLocalStorage so it won't
                        // get captured by Keycloak's local DB and shown via the Admin console
                        log.infov("[Keycloak UserModel Delegate] Setting remote user attribute {0} with values {1}", name, values);
                        user.setFavouriteLine(values.get(0));
                        break;
                    default:
                        log.infov("[Keycloak UserModel Delegate] Setting local user attribute {0} with values {1}", name, values);
                        super.setAttribute(name, values);
                }
            }

            @Override
            public void setFirstName(String firstName) {
                log.infov("[Keycloak UserModel Delegate] Setting firstName: firstName={0}", firstName);
                user.setFirstName(firstName);
            }

            @Override
            public void setLastName(String lastName) {
                log.infov("[Keycloak UserModel Delegate] Setting lastName: lastName={0}", lastName);
                user.setLastName(lastName);
            }

            @Override
            public void setEmail(String email) {
                log.infov("[Keycloak UserModel Delegate] Setting email: email={0}", email);
                user.setEmail(email);
            }
        };
    }

    /* ImportedUserValidation interface implementation (Start) */
    @Override
    public UserModel validate(RealmModel realm, UserModel user) {
        return new UserModelDelegate(user) {
            @Override
            public void setAttribute(String name, List<String> values) {
                // do nothing as we are using a delegate to let this custom storage provider
                // to proxy imported users and disable their attribute setting
            }
        };
    }

    /* ImportedUserValidation interface implementation (End) */
}
