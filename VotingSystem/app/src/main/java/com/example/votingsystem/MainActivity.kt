package com.example.votingsystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.Date

// Data Models
data class User(
    val id: Int,
    val username: String,
    val email: String,
    val created_at: Date
)

data class UserCreate(
    val username: String,
    val email: String,
    val password: String
)

data class Feature(
    val id: Int,
    val title: String,
    val description: String,
    val user_id: Int,
    val username: String,
    val vote_count: Int,
    val status: String,
    val created_at: Date,
    val user_voted: Boolean
)

data class FeatureCreate(
    val title: String,
    val description: String
)

data class Token(
    val access_token: String,
    val token_type: String
)

data class VoteResponse(
    val message: String,
    val voted: Boolean
)

// API Interface
interface VotingSystemApi {
    @POST("register")
    suspend fun register(@Body user: UserCreate): User

    @FormUrlEncoded
    @POST("token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Token

    @GET("me")
    suspend fun getMe(@Header("Authorization") token: String): User

    @GET("features")
    suspend fun getFeatures(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100,
        @Query("sort_by") sortBy: String = "votes"
    ): List<Feature>

    @POST("features")
    suspend fun createFeature(
        @Header("Authorization") token: String,
        @Body feature: FeatureCreate
    ): Feature

    @POST("features/{id}/vote")
    suspend fun voteFeature(
        @Header("Authorization") token: String,
        @Path("id") featureId: Int
    ): VoteResponse

    @DELETE("features/{id}")
    suspend fun deleteFeature(
        @Header("Authorization") token: String,
        @Path("id") featureId: Int
    ): Map<String, String>
}

// API Client
object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8000/" // Use 10.0.2.2 for Android emulator

    val api: VotingSystemApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VotingSystemApi::class.java)
    }
}

// ViewModel
class VotingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(VotingUiState())
    val uiState: StateFlow<VotingUiState> = _uiState

    private val api = ApiClient.api

    init {
        loadFeatures()
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            try {
                val token = api.login(username, password)
                _uiState.value = _uiState.value.copy(
                    token = "Bearer ${token.access_token}",
                    isLoggedIn = true,
                    currentUser = username,
                    currentScreen = Screen.FEATURES
                )
                loadFeatures()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Login failed: ${e.message}"
                )
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            try {
                api.register(UserCreate(username, email, password))
                _uiState.value = _uiState.value.copy(
                    currentScreen = Screen.LOGIN,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Registration failed: ${e.message}"
                )
            }
        }
    }

    fun loadFeatures() {
        viewModelScope.launch {
            try {
                val features = api.getFeatures()
                _uiState.value = _uiState.value.copy(
                    features = features,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load features: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun createFeature(title: String, description: String) {
        viewModelScope.launch {
            try {
                val token = _uiState.value.token ?: return@launch
                api.createFeature(token, FeatureCreate(title, description))
                loadFeatures()
                _uiState.value = _uiState.value.copy(
                    showCreateDialog = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to create feature: ${e.message}"
                )
            }
        }
    }

    fun voteFeature(featureId: Int) {
        viewModelScope.launch {
            try {
                val token = _uiState.value.token ?: return@launch
                api.voteFeature(token, featureId)
                loadFeatures()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to vote: ${e.message}"
                )
            }
        }
    }

    fun setScreen(screen: Screen) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
    }

    fun showCreateDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCreateDialog = show)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun logout() {
        _uiState.value = VotingUiState()
        loadFeatures()
    }
}

// UI State
data class VotingUiState(
    val currentScreen: Screen = Screen.LOGIN,
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val token: String? = null,
    val currentUser: String? = null,
    val features: List<Feature> = emptyList(),
    val error: String? = null,
    val showCreateDialog: Boolean = false
)

enum class Screen {
    LOGIN, REGISTER, FEATURES
}

// Main Activity
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VotingSystemTheme {
                VotingSystemApp()
            }
        }
    }
}

@Composable
fun VotingSystemApp(viewModel: VotingViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (uiState.currentScreen) {
            Screen.LOGIN -> LoginScreen(viewModel)
            Screen.REGISTER -> RegisterScreen(viewModel)
            Screen.FEATURES -> FeaturesScreen(viewModel)
        }

        uiState.error?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("Error") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun LoginScreen(viewModel: VotingViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Voting System",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.login(username, password) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }

        TextButton(
            onClick = { viewModel.setScreen(Screen.REGISTER) }
        ) {
            Text("Don't have an account? Register")
        }

        TextButton(
            onClick = { viewModel.setScreen(Screen.FEATURES) }
        ) {
            Text("Browse as Guest")
        }
    }
}

@Composable
fun RegisterScreen(viewModel: VotingViewModel) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Register",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.register(username, email, password) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
        }

        TextButton(
            onClick = { viewModel.setScreen(Screen.LOGIN) }
        ) {
            Text("Already have an account? Login")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(viewModel: VotingViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feature Requests") },
                actions = {
                    if (uiState.isLoggedIn) {
                        IconButton(onClick = { viewModel.showCreateDialog(true) }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Feature")
                        }
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.Default.Logout, contentDescription = "Logout")
                        }
                    } else {
                        TextButton(onClick = { viewModel.setScreen(Screen.LOGIN) }) {
                            Text("Login")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { viewModel.loadFeatures() }) {
                    Text("Sort by Votes")
                }
                Button(onClick = { viewModel.loadFeatures() }) {
                    Text("Sort by Date")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(uiState.features) { feature ->
                    FeatureItem(feature, uiState.isLoggedIn) {
                        viewModel.voteFeature(feature.id)
                    }
                }
            }
        }

        if (uiState.showCreateDialog) {
            CreateFeatureDialog(
                onDismiss = { viewModel.showCreateDialog(false) },
                onCreate = { title, desc -> viewModel.createFeature(title, desc) }
            )
        }
    }
}

@Composable
fun FeatureItem(feature: Feature, canVote: Boolean, onVote: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(feature.title, style = MaterialTheme.typography.titleLarge)
            Text(feature.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("By: ${feature.username}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Votes: ${feature.vote_count}")
                    if (canVote) {
                        IconButton(onClick = onVote) {
                            Icon(
                                if (feature.user_voted) Icons.Default.ThumbDown else Icons.Default.ThumbUp,
                                contentDescription = "Vote"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateFeatureDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Feature") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && description.isNotBlank()) {
                        onCreate(title, description)
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
