package com.flyer.keycloak.extension;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.Config;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

/**
 * Keycloak file based user storage provider factory
 *
 * @author Ruifeng Ma
 * @since 2019-May-25
 */

@JBossLog
public class FileUserStorageProviderFactory implements UserStorageProviderFactory<FileUserStorageProvider> {
    @Override
    public void init(Config.Scope config) {
        String someProperty = config.get("someProperty");
        log.infov("Configured {0} with someProperty: {1}", this, someProperty);
    }

    @Override
    public FileUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        log.infov("Creating provider ...");
        String userHomeDir = System.getProperty("user.home");
        String fileName = "userDB.json";

        FileUserRepository userRepository = FileUserRepository.getInstance(userHomeDir + "/" +fileName);

        return new FileUserStorageProvider(session, model, userRepository);
    }

    @Override
    public String getId() {
        return "file-user-storage-provider";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {

        // this configuration is configurable in the admin-console
        return ProviderConfigurationBuilder.create()
                .property()
                .name("myParam")
                .label("My Param")
                .helpText("Some Description")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("some value")
                .add()
                // more properties
                // .property()
                // .add()
                .build();
    }
}
