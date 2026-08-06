package dev.gaphunter.gitlabcicompanion.credentials

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * One Personal Access Token per GitLab instance, never a single shared
 * field -- the real competitor complaint this answers is "adding a
 * second PAT breaks the first". Keyed by instance URL so two instances
 * (e.g. gitlab.com and a self-hosted one) never collide.
 *
 * Uses the plain synchronous `get`/`set` declared on the stable
 * `PasswordStorage`/`CredentialStore` interfaces, not `PasswordSafe`'s
 * own `getAsync`/`Promise` -- `getAsync` compiles fine but is an
 * *unresolved method* at runtime on 3 of this catalog's 6 target IDE
 * versions (253/261/262), confirmed by `verifyPlugin` itself, not a
 * guess. `get` is called off the EDT via `executeOnPooledThread` by
 * the caller, the same "heavy work off the UI thread" pattern already
 * used everywhere else in this catalog -- see
 * INTELLIJ_PLATFORM_KNOWLEDGE.md section H for the full story.
 */
object GitlabCredentialsStore {

    private fun attributesFor(instanceUrl: String) =
        CredentialAttributes("GitlabCiCompanion:$instanceUrl", "token")

    /** Blocking I/O -- call from a background thread, never the EDT. */
    fun getToken(instanceUrl: String): String? =
        PasswordSafe.instance.get(attributesFor(instanceUrl))?.getPasswordAsString()

    fun setToken(instanceUrl: String, token: String) {
        PasswordSafe.instance.set(attributesFor(instanceUrl), Credentials("token", token), false)
    }

    fun removeToken(instanceUrl: String) {
        PasswordSafe.instance.set(attributesFor(instanceUrl), null, false)
    }
}
