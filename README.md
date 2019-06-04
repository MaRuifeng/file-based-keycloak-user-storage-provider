# File based Keycloak user storage provider

The [User Storage SPI](https://www.keycloak.org/docs/6.0/server_development/#_user-storage-spi) of Keycloak can be used to write extensions that connect to external user databases and credential stores for customized user federation. 
The built-in [LDAP](https://github.com/keycloak/keycloak/blob/b478472b3578b8980d7b5f1642e91e75d1e78d16/federation/ldap/src/main/java/org/keycloak/storage/ldap/LDAPStorageProvider.java) and [ActiveDirectory](https://github.com/keycloak/keycloak/blob/b478472b3578b8980d7b5f1642e91e75d1e78d16/federation/kerberos/src/main/java/org/keycloak/federation/kerberos/KerberosFederationProvider.java) providers are an implementation of the SPI in default Keycloak distributions.

This example demonstrates how to write such an extension using a JSON file based user repository as the external user & credential database. The JSON file will be created as `userDB.json` under the user's home directory the first time this extension is run.

The code base is developed by extending the example given in this [documentation](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.1/html/server_developer_guide/user-storage-spi) from RedHat. 
By following the factory design pattern, a `FileUserStorageProviderFactory` creates a `FileUserStorageProvider` instance in each Keycloak transaction, which further leverages a `UserModel` instance as an adapter to bridge to the custom user model defined in the file based repository.  

The `FileUserStorageProvider` implements [these provider capability interfaces](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.1/html/server_developer_guide/user-storage-spi#provider_capability_interfaces) to enable user lookup & update, authentication, query, registration & removal. 

## Technical discussions

During development, this [problem](https://stackoverflow.com/questions/56272637/how-do-i-write-a-simple-transaction-wrapper-in-a-keycloak-spi-extension) was raised up as the author had little idea on how to persist user data back to the repository. 
As a coarse solution, the author decided to put file data persistence step in the `close()` method which executes at the end of a transaction. This code can be found in the `import_strategy` branch.
> Update by author at a later stage

A self-defined Keycloak transaction is enlisted after the main authentication transaction to persist user data. This occurs in the `setAttribute()` method of the user model adapter, found in both the `master` and `demo` branches. 

### Federated user storage implementation

This implementation is found in the `master` branch. It leverages the [federated user storage](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.1/html/server_developer_guide/user-storage-spi#augmenting_external_storage) feature provided by Keycloak, and only stores additional attributes and role mappings etc. that cannot be handled by the external storage into the local database.
A `UserModel` instance is created by inheriting the abstract `AbstractUserAdapterFederatedStorage` class, which provides customized getters and setters to manage user data via either federated or external storage, or both. 
The `demo` branch shows how certain attributes can be skipped by the federated user storage by overriding the `setAttribute()` method. 

###  Import strategy implementation

This implementation is found in the `import_strategy` branch. It leverages the [user import](https://access.redhat.com/documentation/en-us/red_hat_single_sign-on/7.1/html/server_developer_guide/user-storage-spi#import_implementation_strategy) feature provided by Keycloak and creates a local copy of each user in its own database. 
The `ImportedUserValidationInterface` provides a way to delegate imported users and override their default behavior. In this example, there is a requirement that further attribute setting needs to be avoided on imported users, hence the `setAttribute()` function is overridden to avoid that.  

## Build

`mvn clean install` creates a `jar` file in the target folder. 

## Deploy to a Keycloak server running in the standalone mode

Keycloak server version: 6.0.1
* Create a file with path `META-INF/services/org.keycloak.storage.UserStorageProviderFactory` which contains the classname of the provider factory (included in this repo)
* Run the build command to obtain the `jar` artifact
* Copy the `jar` file to `keycloak-6.0.1/standalone/deployments` found in Keycloak's runtime directory, and the Keycloak server should be able to detect and install it
* Open the admin console of the Keycloak server, and add in this customized provider in the User Federation section

## Verified user case scenarios 

Suppose there is a web application that leverages this Keycloak server for identity and access management. 

* Users in the file based repository get loaded into Keycloak's internal user database
* Successful login with such users
* Brokered external SAML/OIDC identify provider
    * Logged-in users get loaded/updated into Keyloak's internal user database as well as the file based repository
    * Non-common attributes (e.g. `favouriteLine` in this code repo) are successfully managed (skip setting when needed) as long as they are mapped correctly
* Once a user record is removed via the admin console, it also gets deleted from the file based repository
  
## Author

Ruifeng Ma (mrfflyer@gmail.com)


