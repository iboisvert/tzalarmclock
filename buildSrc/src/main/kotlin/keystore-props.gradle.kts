import java.io.FileInputStream
import java.util.Properties

// Loads the release-signing keystore properties from the `secrets` submodule
// (see .gitmodules) into an extra property named `keystoreProperties`, so no
// secret values are ever committed to this repo. Consumers read it with:
//   val keystoreProperties: Properties by extra
val keystorePropertiesFile = rootProject.file("secrets/keystore.properties")
check(keystorePropertiesFile.exists()) {
    "Missing $keystorePropertiesFile - run `git submodule update --init` " +
        "to fetch the secrets submodule."
}

val keystoreProperties = Properties().apply {
    FileInputStream(keystorePropertiesFile).use { load(it) }
}

extra["keystoreProperties"] = keystoreProperties
