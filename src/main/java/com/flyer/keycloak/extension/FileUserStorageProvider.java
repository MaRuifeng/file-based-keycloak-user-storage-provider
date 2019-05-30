package com.flyer.keycloak.extension;

import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;
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
        UserRegistrationProvider {
    private final KeycloakSession session;
    private final ComponentModel model; // represents how the provider is enabled and configured within a specific realm
    private final FileUserRepository userRepository;
    private final Map<String, UserModel> loadedUsers;

    @Getter private boolean userDataChanged; // flag for user repository persistence, could be implemented in a more granular way

    public FileUserStorageProvider(KeycloakSession session, ComponentModel model, FileUserRepository userRepository) {
        this.session = session;
        this.model = model;
        this.userRepository = userRepository;
        this.loadedUsers = new HashMap<>();
        this.userDataChanged = false;
    }

    /* UserLookupProvider interface implementation (Start) */
    @Override
    public void close() {
        if (userDataChanged) {
            log.infov("[UserLookupProvider] Persisting user data changes ...");
            try {
                userRepository.persistUserDataToFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        log.infov("[UserLookupProvider] Closing at the end of the transaction.");
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
        UserModel adapter = loadedUsers.get(username);
        if (adapter == null) {
//            session.userFederatedStorage().getStoredUsers(realm, 0, Integer.MAX_VALUE)
//                    .stream().forEach(user -> log.infov("Stored user in federated storage: {0}", user));
            log.infov("Looking up user from the repository via username: username={0} realm={1}", username, realm.getId());
            User user = userRepository.getUser(username);
            log.infov("Found user: {0}", user);
            if (user != null) {
                adapter = createAdapter(realm, user);
                loadedUsers.put(username, adapter);
            }
        }
        return adapter;
    }

    private UserModel createAdapter(RealmModel realm, User user) {
        return new AbstractUserAdapterFederatedStorage(session, realm, model) { // anonymous class inheriting the abstract parent
            @Override
            public String getUsername() {
                log.infov("[Keycloak UserModel Adapter] Getting username ....");
                return user.getUsername();
            }

            @Override
            public String getFirstName() {
                log.infov("[Keycloak UserModel Adapter] Getting firstName ....");
                return user.getFirstName();
            }

            @Override
            public String getLastName() {
                log.infov("[Keycloak UserModel Adapter] Getting lastName ....");
                return user.getLastName();
            }

            @Override
            public String getEmail() {
                log.infov("[Keycloak UserModel Adapter] Getting email ....");
                return user.getEmail();
            }

            @Override
            public String getFirstAttribute(String name) {
                log.infov("[Keycloak UserModel Adapter] Getting first value of attribute {0} ....", name);
                return getFederatedStorage().getAttributes(realm, this.getId()).getFirst(name);
            }

            @Override
            public Map<String, List<String>> getAttributes() {
                log.infov("[Keycloak UserModel Adapter] Getting all attributes ....");
                return getFederatedStorage().getAttributes(realm, this.getId());
            }

            @Override
            public List<String> getAttribute(String name) {
                log.infov("[Keycloak UserModel Adapter] Getting values of attribute {0} ....", name);
                return getFederatedStorage().getAttributes(realm, this.getId()).get(name);
            }

            @Override
            public void setUsername(String username) {
                log.infov("[Keycloak UserModel Adapter] Setting username: {0}", username);
                user.setUsername(username);
                userDataChanged = true;
            }

            @Override
            public void setFirstName(String firstName) {
                log.infov("[Keycloak UserModel Adapter] Setting firstName: firstName={0}", firstName);
                user.setFirstName(firstName);
                userDataChanged = true;
            }

            @Override
            public void setLastName(String lastName) {
                log.infov("[Keycloak UserModel Adapter] Setting lastName: lastName={0}", lastName);
                user.setLastName(lastName);
                userDataChanged = true;
            }

            @Override
            public void setEmail(String email) {
                log.infov("[Keycloak UserModel Adapter] Setting email: email={0}", email);
                user.setEmail(email);
                userDataChanged = true;
            }

            @Override
            public void setSingleAttribute(String name, String value) {
                log.infov("[Keycloak UserModel Adapter] Setting attribute {0} with value {1}", name, value);
                getFederatedStorage().setSingleAttribute(realm, this.getId(), name, value);
                userDataChanged = true;
            }

            @Override
            public void setAttribute(String name, List<String> values) {
                log.infov("[Keycloak UserModel Adapter] Setting attribute {0} with values {1}", name, values);
                switch (name) {
                    case "favouriteLine":
                        // purposely skip attribute setting to federatedStorage so it won't
                        // get captured by Keycloak's local DB and shown via the Admin console
                        user.setFavouriteLine(values.get(0));
                        break;
                    default:
                        getFederatedStorage().setAttribute(realm, this.getId(), name, values);
                }
                userDataChanged = true;
            }
        };
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
}
