import java.io.FileInputStream
import java.util.Properties

// Loads the release-signing keystore properties from the `secrets` submodule
// (see .gitmodules) into an extra property named `keystoreProperties`, so no
// secret values are ever committed to this repo. Consumers read it with:
//   val keystoreProperties: Properties? by extra
//
// This is null when the submodule isn't checked out, which is expected for
// a contributor who doesn't have access to that private repo (it holds the
// real release signing key) - debug builds don't need it at all. Consumers
// are responsible for failing loudly, but only for the release tasks that
// actually need signing; see app/build.gradle.kts.
val keystorePropertiesFile = rootProject.file("secrets/keystore.properties")

val keystoreProperties: Properties? = if (keystorePropertiesFile.exists()) {
    Properties().apply {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
} else {
    null
}

extra["keystoreProperties"] = keystoreProperties
