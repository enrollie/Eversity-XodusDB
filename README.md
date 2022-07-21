# Eversity Xodus DB

Plugin for Eversity, providing a Xodus database interface for Eversity.

# Using

Copy built JAR to the Eversity plugins directory and pass configuration environment variables to the Eversity server (
list of variables is provided below).

# Building

```bash
./gradlew clean shadowJar
```

# Configuration

Xodus database plugin is configured through environment variables.

| Variable                | Description                                                           | Default value |
|-------------------------|-----------------------------------------------------------------------|---------------|
| `XODUS_DATABASE_HOME`   | Database home directory                                               | `./database`  |
| `XODUS_USE_ENCRYPTION`  | Flag describing whether database should be using an encryption or not | `false`       |
| `XODUS_CIPHER_KEY`      | Cipher key used to encrypt database                                   | Not set       |
| `XODUS_CIPHER_BASIC_IV` | Cipher basic IV used to encrypt database                              | Not set       |
| `XODUS_BACKUP_ON_STOP`  | Whether database should be backed up on server stop                   | `false`       |

# License

Licensed under the MIT license.
