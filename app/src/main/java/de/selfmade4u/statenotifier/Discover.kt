package de.selfmade4u.statenotifier

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.UUID


@Preview
@Composable
fun Discover() {
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(
                TextFieldValue("")
            )
        }
        TextField(
            label = { Text("Service name") },
            value = text,
            onValueChange = { newText ->
                text = newText
            }
        )
        val context = LocalContext.current
        Button(onClick = {
            // https://developer.android.com/training/articles/keystore

            /*
             * Generate a new EC key pair entry in the Android Keystore by
             * using the KeyPairGenerator API. The private key can only be
             * used for signing or verification and only with SHA-256 or
             * SHA-512 as the message digest.
             */
            val alias = UUID.randomUUID().toString()
            val kpg: KeyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )
            // https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec
            val parameterSpec: KeyGenParameterSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN
            )
                .run {
                    setDigests(KeyProperties.DIGEST_SHA512)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    build()
                }

            kpg.initialize(parameterSpec)

            val kp = kpg.generateKeyPair()

            /*
 * Use a PrivateKey in the KeyStore to create a signature over
 * some data.
 */
            val ks: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }
            val entry: KeyStore.Entry = ks.getEntry(alias, null)
            if (entry !is KeyStore.PrivateKeyEntry) {
                Log.w("StateNotifier", "Not an instance of a PrivateKeyEntry")
                throw Error("fail")
            }
            val signature: ByteArray = Signature.getInstance("SHA512withRSA/PSS").run {
                initSign(entry.privateKey)
                update("this is test data".toByteArray())
                sign()
            }

            scope.launch {
                AppDatabase.getDatabase(context).advertisedServiceDao()
                    .insertAll(AdvertisedService(alias, text.text))
            }
        }) {
            Text("Create service")
        }
    }
}