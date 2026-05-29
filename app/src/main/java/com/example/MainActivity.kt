package com.example

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.database.ChatMessageEntity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.camera.RecoveryCameraManager
import com.example.network.NetworkClient
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var cameraManager: RecoveryCameraManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraManager = RecoveryCameraManager(this)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()
                val currentScreen by viewModel.currentScreen.collectAsState()
                var isChatOpen by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (currentScreen != "splash" && currentScreen != "login" && currentScreen != "register" && currentScreen != "onboarding") {
                            SMREPTopAppBar()
                        }
                    },
                    bottomBar = {
                        if (currentScreen != "splash" && currentScreen != "login" && currentScreen != "register" && currentScreen != "onboarding") {
                            SMREPBottomNavigation(
                                currentScreen = currentScreen,
                                onSelect = { viewModel.navigateTo(it) }
                            )
                        }
                    },
                    floatingActionButton = {
                        if (currentScreen != "splash" && currentScreen != "login" && currentScreen != "register" && currentScreen != "onboarding" && !isChatOpen) {
                            FloatingActionButton(
                                onClick = { isChatOpen = true },
                                containerColor = Color(0xFFE8DEF8),
                                contentColor = Color(0xFF1D192B),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("chat_fab")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = "Open Chatbox Support"
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (currentScreen) {
                                "splash" -> SplashScreen(viewModel)
                                "login" -> LoginScreen(viewModel)
                                "register" -> RegisterScreen(viewModel)
                                "onboarding" -> ConsentOnboardingScreen(viewModel)
                                "dashboard" -> DashboardScreen(viewModel, triggerCamera = { triggerCameraCapture(viewModel) })
                                "telemetry" -> TelemetryScreen(viewModel)
                                "evidence" -> EvidenceScreen(viewModel, triggerCamera = { triggerCameraCapture(viewModel) })
                                "settings" -> SettingsScreen(viewModel)
                                else -> DashboardScreen(viewModel, triggerCamera = { triggerCameraCapture(viewModel) })
                            }
                        }

                        // Sliding Chatbox Overlay using standard Enter/Exit transition logic
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isChatOpen,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it }),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .zIndex(5f)
                        ) {
                            ChatboxOverlay(
                                viewModel = viewModel,
                                onClose = { isChatOpen = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun triggerCameraCapture(viewModel: MainViewModel) {
        val path = filesDir
        viewModel.logTrace("Requesting CameraX snapshot capture binding.")
        cameraManager.captureSnapshot(
            lifecycleOwner = this,
            onSuccess = { file ->
                viewModel.saveCameraEvidence(file)
                runOnUiThread {
                    Toast.makeText(this, "Evidence snapshot registered!", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { ex ->
                viewModel.logTrace("CameraX snapshot error: ${ex.message}")
            }
        )
    }
}

// Reusable custom layout badge chip for Sleek Interface style
@Composable
fun BadgeChip(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF1D192B), RoundedCornerShape(100.dp))
            .border(1.dp, Color(0xFF4F378B), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD0BCFF)
        )
    }
}

// Custom Premium Top Header matching the mockup aesthetic
@Composable
fun SMREPTopAppBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF4F378B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield Icon",
                    tint = Color(0xFFD0BCFF),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column {
                Text(
                    text = "SMREP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "SHIELD PROTECTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD0BCFF),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
        
        // Circular profile status boundary mockup
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(2.dp, Color(0xFF49454F), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF313033))
            )
        }
    }
}

// Bottom Navigation Bar with matching M3 custom item aesthetics
@Composable
fun SMREPBottomNavigation(
    currentScreen: String,
    onSelect: (String) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF2B2930),
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, Color(0xFF49454F), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavigationBarItem(
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1D192B),
                selectedTextColor = Color(0xFFE6E1E5),
                indicatorColor = Color(0xFFE8DEF8),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            icon = { Icon(Icons.Default.Shield, contentDescription = "Dashboard") },
            label = { Text("Dashboard", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            selected = currentScreen == "dashboard" || currentScreen == "splash",
            onClick = { onSelect("dashboard") }
        )
        NavigationBarItem(
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1D192B),
                selectedTextColor = Color(0xFFE6E1E5),
                indicatorColor = Color(0xFFE8DEF8),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Telemetry") },
            label = { Text("Evidence", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            selected = currentScreen == "evidence",
            onClick = { onSelect("evidence") }
        )
        NavigationBarItem(
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1D192B),
                selectedTextColor = Color(0xFFE6E1E5),
                indicatorColor = Color(0xFFE8DEF8),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            icon = { Icon(Icons.Default.Notifications, contentDescription = "Alerts") },
            label = { Text("Alerts", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            selected = currentScreen == "telemetry",
            onClick = { onSelect("telemetry") }
        )
        NavigationBarItem(
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF1D192B),
                selectedTextColor = Color(0xFFE6E1E5),
                indicatorColor = Color(0xFFE8DEF8),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            selected = currentScreen == "settings",
            onClick = { onSelect("settings") }
        )
    }
}

// 1. Splash Screen
@Composable
fun SplashScreen(viewModel: MainViewModel) {
    val user by viewModel.currentUser.collectAsState()
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        if (user != null) {
            val onboardingCompleted = viewModel.repository.getSetting("onboarding_completed") == "true"
            if (onboardingCompleted) {
                viewModel.navigateTo("dashboard")
            } else {
                viewModel.navigateTo("onboarding")
            }
        } else {
            viewModel.navigateTo("login")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "SMREP",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Secure Mobile Recovery & Evidence Platform",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(strokeWidth = 3.dp)
        }
    }
}

// 2. Login Screen
@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var email by remember { mutableStateOf("") }
    val isProcessing by viewModel.isProcessing.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = "User info",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Verify identity to access recovery tools", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Registered Email Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.performLogin(email) },
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Access Account")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = { viewModel.navigateTo("register") }) {
                Text("Register New Protected Device")
            }
        }
    }
}

// 3. Register Screen
data class CountryCode(val name: String, val code: String, val flag: String)

@Composable
fun RegisterScreen(viewModel: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var phoneRaw by remember { mutableStateOf("") }
    val isProcessing by viewModel.isProcessing.collectAsState()

    val countries = remember {
        listOf(
            CountryCode("United States", "+1", "🇺🇸"),
            CountryCode("India", "+91", "🇮🇳"),
            CountryCode("United Kingdom", "+44", "🇬🇧"),
            CountryCode("Canada", "+1", "🇨🇦"),
            CountryCode("Australia", "+61", "🇦🇺"),
            CountryCode("Singapore", "+65", "🇸🇬"),
            CountryCode("United Arab Emirates", "+971", "🇦🇪"),
            CountryCode("Germany", "+49", "🇩🇪"),
            CountryCode("France", "+33", "🇫🇷"),
            CountryCode("Japan", "+81", "🇯🇵"),
            CountryCode("Brazil", "+55", "🇧🇷"),
            CountryCode("South Africa", "+27", "🇿🇦"),
            CountryCode("Saudi Arabia", "+966", "🇸🇦"),
            CountryCode("Nigeria", "+234", "🇳🇬"),
            CountryCode("Mexico", "+52", "🇲🇽"),
            CountryCode("Spain", "+34", "🇪🇸"),
            CountryCode("Italy", "+39", "🇮🇹"),
            CountryCode("South Korea", "+82", "🇰🇷"),
            CountryCode("China", "+86", "🇨🇳"),
            CountryCode("Egypt", "+20", "🇪🇬"),
            CountryCode("Viet Nam", "+84", "🇻🇳"),
            CountryCode("Russian Federation", "+7", "🇷🇺"),
            CountryCode("Bangladesh", "+880", "🇧🇩"),
            CountryCode("Pakistan", "+92", "🇵🇰"),
            CountryCode("Indonesia", "+62", "🇮🇩"),
            CountryCode("Ukraine", "+380", "🇺🇦"),
            CountryCode("Netherlands", "+31", "🇳🇱"),
            CountryCode("Sweden", "+46", "🇸🇪"),
            CountryCode("Switzerland", "+41", "🇨🇭"),
            CountryCode("Turkey", "+90", "🇹🇷"),
            CountryCode("Malaysia", "+60", "🇲🇾"),
            CountryCode("Philippines", "+63", "🇵🇭"),
            CountryCode("Poland", "+48", "🇵🇱"),
            CountryCode("Colombia", "+57", "🇨🇴"),
            CountryCode("Argentina", "+54", "🇦🇷"),
            CountryCode("Chile", "+56", "🇨🇱"),
            CountryCode("Peru", "+51", "🇵🇪")
        )
    }

    var selectedCountry by remember { mutableStateOf(countries[0]) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            countries
        } else {
            countries.filter {
                it.name.contains(searchQuery, ignoreCase = true) || 
                it.code.contains(searchQuery)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = "Shield",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Device Binding", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Register SMREP protection on this device", color = MaterialTheme.colorScheme.secondary)

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Owner Email Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Country Picker & Mobile Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Country Code Picker Box
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(56.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .clickable { dropdownExpanded = true }
                        .padding(horizontal = 12.dp)
                        .testTag("country_picker_dropdown"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedCountry.flag, fontSize = 20.sp)
                        Text(
                            text = selectedCountry.code,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Country",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier
                            .width(260.dp)
                            .heightIn(max = 350.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    ) {
                        // Search field within dropdown menu
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search country...", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                        if (filteredCountries.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No countries found", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp) },
                                onClick = {}
                            )
                        } else {
                            filteredCountries.forEach { country ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(country.flag, fontSize = 20.sp)
                                            Text(
                                                text = country.code,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = country.name,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedCountry = country
                                        dropdownExpanded = false
                                        searchQuery = "" // Reset query
                                    }
                                )
                            }
                        }
                    }
                }

                // Phone number text field
                OutlinedTextField(
                    value = phoneRaw,
                    onValueChange = { input ->
                        // Only allow numbers, spaces, and hyphens
                        phoneRaw = input.filter { it.isDigit() || it == ' ' || it == '-' }
                    },
                    label = { Text("Mobile Number") },
                    placeholder = { Text("12345 67890") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                    ),
                    modifier = Modifier.weight(1f).testTag("mobile_number_input")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { 
                    val cleanDigits = phoneRaw.filter { it.isDigit() }
                    val combinedPhone = "${selectedCountry.code}$cleanDigits"
                    viewModel.performRegisterAndRegisterDevice(email, combinedPhone) 
                },
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("secure_register_button")
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Secure & Register Device")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = { viewModel.navigateTo("login") }) {
                Text("Already registered? Login")
            }
        }
    }
}

// 4. Onboarding / Consent Screen
@Composable
fun ConsentOnboardingScreen(viewModel: MainViewModel) {
    val locationConsent by viewModel.locationConsent.collectAsState()
    val evidenceConsent by viewModel.evidenceConsent.collectAsState()
    val lostModeConsent by viewModel.lostModeConsent.collectAsState()
    val privacyConsent by viewModel.privacyConsent.collectAsState()

    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        
        viewModel.saveConsent("location", fineGranted)
        viewModel.saveConsent("evidence", cameraGranted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Mandatory Consent Onboarding", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("SMREP requires explicit permission to enforce device-recovery rules.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ConsentRow(
                        title = "Location Collection (GPS)",
                        description = "Enables real-time and periodic telemetry recording to map coordinates during recovery conditions.",
                        isChecked = locationConsent,
                        onCheckedChange = { 
                            if (it) {
                                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            } else {
                                viewModel.saveConsent("location", false)
                            }
                        }
                    )
                }
                item {
                    ConsentRow(
                        title = "Recovery Snaps (Camera)",
                        description = "Enables automatic front-camera image capture upon security event triggers to preserve intruder evidence.",
                        isChecked = evidenceConsent,
                        onCheckedChange = {
                            if (it) {
                                launcher.launch(arrayOf(Manifest.permission.CAMERA))
                            } else {
                                viewModel.saveConsent("evidence", false)
                            }
                        }
                    )
                }
                item {
                    ConsentRow(
                        title = "Offline Autonomic Tracking",
                        description = "Monitors system events, airplane transition state, and cellular card ICCID swap metrics offline to trigger recovery logs.",
                        isChecked = lostModeConsent,
                        onCheckedChange = { viewModel.saveConsent("lostmode", it) }
                    )
                }
                item {
                    ConsentRow(
                        title = "Accept Recovery Privacy Policy",
                        description = "All collected packets are stored locally inside private app directory sqlite and AES-GCM encrypted before upload to your hosted backend.",
                        isChecked = privacyConsent,
                        onCheckedChange = { viewModel.saveConsent("privacy", it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.completeOnboarding() },
                enabled = locationConsent && evidenceConsent && lostModeConsent && privacyConsent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Lock Settings & Proceed")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ConsentRow(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
        }
    }
}

// 5. Dashboard Screen
@Composable
fun DashboardScreen(viewModel: MainViewModel, triggerCamera: () -> Unit) {
    val device by viewModel.currentDevice.collectAsState()
    val events by viewModel.allEvents.collectAsState()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val telemetryList by viewModel.allTelemetry.collectAsState()

    val latestTelemetry = telemetryList.firstOrNull()
    val batteryValue = latestTelemetry?.battery ?: 82
    val batteryPct = batteryValue / 100f
    
    val latLonText = latestTelemetry?.let { 
        "${String.format(Locale.US, "%.2f", it.lat)}, ${String.format(Locale.US, "%.2f", it.lon)}" 
    } ?: "40.71, -74.00"
    
    val accuracyText = latestTelemetry?.let { 
        "Accuracy: ${String.format(Locale.US, "%.1f", it.accuracy)}m" 
    } ?: "Accuracy: 3.2m"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Security Status Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2B2930)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular state representation with dynamic colors based on lost state
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(if (device?.isLostMode == true) Color(0xFF381E72) else Color(0xFF381E72)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(4.dp, if (device?.isLostMode == true) Color(0xFFB3261E) else Color(0xFFD0BCFF), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (device?.isLostMode == true) Icons.Default.Warning else Icons.Default.Shield,
                            contentDescription = "Status Logo",
                            tint = if (device?.isLostMode == true) Color(0xFFB3261E) else Color(0xFFD0BCFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (device?.isLostMode == true) "Device Lost" else "Device Secure",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E5)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Last Telemetry Sync: Just now",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCAC4D0)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BadgeChip(text = "INTEGRITY: PASS")
                    BadgeChip(text = "AES-GCM: ON")
                }
            }
        }

        // Telemetry Grid row with 2 side-by-side adaptive subcards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Battery Level Display Box matching mockup details
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "BATTERY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "$batteryValue",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6E1E5)
                        )
                        Text(
                            text = "%",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFCAC4D0),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    // Animated-style sleek progress indicator line
                    LinearProgressIndicator(
                        progress = { batteryPct },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFD0BCFF),
                        trackColor = Color(0xFF49454F)
                    )
                }
            }

            // Location coordinates Card Box matching position mockup label
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "POSITION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = latLonText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5)
                    )
                    Text(
                        text = accuracyText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCAC4D0)
                    )
                }
            }
        }

        // Critical Intervention Trigger button
        Button(
            onClick = { viewModel.toggleLostMode(!(device?.isLostMode == true)) },
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (device?.isLostMode == true) Color(0xFF381E72) else Color(0xFFB3261E),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (device?.isLostMode == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Trigger Status"
                )
                Text(
                    text = if (device?.isLostMode == true) "REVOKE LOST STATE" else "ACTIVATE LOST MODE",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }

        // Quick action command row matching snapshot and telemetry buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = triggerCamera,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF313033),
                    contentColor = Color(0xFFD0BCFF)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Camera Tap", modifier = Modifier.size(16.dp))
                    Text("SNAPSHOT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.simulateTelemetryUpload() },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF313033),
                    contentColor = Color(0xFFD0BCFF)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(1.dp, Color(0xFF49454F), RoundedCornerShape(16.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.ShareLocation, contentDescription = "Loc Run", modifier = Modifier.size(16.dp))
                    Text("TELEMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Security Event Logs",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE6E1E5)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (events.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active recovery alerts logged.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCAC4D0)
                        )
                    }
                }
            } else {
                items(events) { event ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2930).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (event.eventType) {
                                    "SIM_CHANGED" -> Icons.Default.SdCard
                                    "DEVICE_OFFLINE" -> Icons.Default.CloudOff
                                    "AIRPLANE_MODE" -> Icons.Default.AirplanemodeActive
                                    "LOCATION_DISABLED" -> Icons.Default.LocationDisabled
                                    else -> Icons.Default.Warning
                                },
                                contentDescription = "Event icon",
                                tint = Color(0xFFB3261E),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = event.eventType,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE6E1E5),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = event.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCAC4D0)
                                )
                                Text(
                                    text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFD0BCFF),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 6. Telemetry Screen
@Composable
fun TelemetryScreen(viewModel: MainViewModel) {
    val telemetryList by viewModel.allTelemetry.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Telemetry Coordination Logs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("SQLite records. Tracked packets every 15 minutes.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (telemetryList.isEmpty()) {
                item {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No telemetry logs compiled.", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            } else {
                items(telemetryList) { tel ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = "GPS", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Lat: ${String.format("%.5f", tel.lat)}, Lon: ${String.format("%.5f", tel.lon)} (~${tel.accuracy}m)", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Battery: ${tel.battery}% | Network: ${tel.network}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (tel.isUploaded) "SYNCED" else "PENDING",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (tel.isUploaded) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                )
                            }
                            Text(
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(tel.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 7. Evidence Screen
@Composable
fun EvidenceScreen(viewModel: MainViewModel, triggerCamera: () -> Unit) {
    val evidenceList by viewModel.allEvidence.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Evidence Snapshot File Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Lawful security visual artifacts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = triggerCamera) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "Manual Photo", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (evidenceList.isEmpty()) {
                item {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("No capture evidence stored yet.", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            } else {
                items(evidenceList) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Check file exists
                            val file = File(item.photoPath)
                            if (file.exists()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = "Evidence image snapshot",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(Color.Gray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Image file deleted or unavailable offline", color = Color.White)
                                }
                            }
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Cryptographic Signature Hash (RSA-SHA256):", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    item.signature.take(36) + "...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        if (item.isUploaded) "ENCRYPTED & SYNCED" else "AES ENCRYPTED - LOCAL",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (item.isUploaded) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 8. Settings & Simulation Console Screen
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val device by viewModel.currentDevice.collectAsState()
    val user by viewModel.currentUser.collectAsState()

    var serverUrl by remember { mutableStateOf<String>(NetworkClient.getBaseUrl()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Admin Platform Command Console", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Simulation tools to verify system security posture.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("API Host Server Sync Port", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { newValue: String -> serverUrl = newValue },
                        singleLine = true,
                        label = { Text("Base URL Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { viewModel.updateServerUrl(serverUrl) }) {
                            Text("Update Sync Address")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Identity Fingerprint Binder", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Owner Key ID: ${user?.id ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                    Text("Hashed SHA-256 Device ID:\n${device?.deviceHash ?: "N/A"}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Simulation Panel Triggers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(
                        onClick = { viewModel.simulateSIMSwap() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.SdCard, contentDescription = "Sim Swaped")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate SIM Swapped Event")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.triggerManualAudit() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = "Check Environment")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Force Lost Mode Logic Audit")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.simulateTelemetryUpload() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Telemetry Sync")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trigger Immediate GPS Telemetry Run")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.requestIntegrityCheck() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = "Check Integrity")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Query Google Play Integrity Engine")
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.resetAppDatabase() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "delete")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wipe Secure Store (Room Reset)")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ChatboxOverlay(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isGenerating by viewModel.isChatGenerating.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages come in or we are generating
    LaunchedEffect(chatMessages.size, isGenerating) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF1D1B20))
            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2930))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF4F378B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Shield Guard AI",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "SMREP Shield AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6E1E5)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF24A148))
                            )
                            Text(
                                text = "Operational & Encrypted",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD0BCFF)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.clearChatHistory() },
                        modifier = Modifier.testTag("chat_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Clear Chat History",
                            tint = Color(0xFFE6E1E5).copy(alpha = 0.6f)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("chat_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Chat",
                            tint = Color(0xFFE6E1E5)
                        )
                    }
                }
            }

            // Scrollable Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (chatMessages.isEmpty()) {
                    // Placeholder Welcome View
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Secure logo",
                            tint = Color(0xFF4F378B).copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "SMREP Secure Shield AI Chat",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6E1E5)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Consult our AI assistant on device lost mode controls, cryptographic evidence validation, simulated event vectors, or Play Store integrity guidelines.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCABEFF)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(chatMessages) { msg ->
                            val isUser = msg.sender == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 280.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isUser) 16.dp else 2.dp,
                                                bottomEnd = if (isUser) 2.dp else 16.dp
                                            )
                                        )
                                        .background(if (isUser) Color(0xFF4F378B) else Color(0xFF27252C))
                                        .border(
                                            1.dp,
                                            if (isUser) Color(0xFF625B71) else Color(0xFF38353E),
                                            RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isUser) 16.dp else 2.dp,
                                                bottomEnd = if (isUser) 2.dp else 16.dp
                                            )
                                        )
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = msg.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isUser) Color(0xFFFFFFFF) else Color(0xFFE6E1E5)
                                    )
                                }
                            }
                        }

                        if (isGenerating) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp))
                                            .background(Color(0xFF27252C))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Shield AI is generating response...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFCABEFF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick suggestion chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Suggested Questions",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFD0BCFF),
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF211F24), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF4F378B), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.sendChatMessage("How does SMREP defend my device in lost mode?")
                            }
                            .padding(8.dp)
                            .testTag("chat_preset_chip_1")
                    ) {
                        Text(
                            text = "Lost Mode Info",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCABEFF)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF211F24), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF4F378B), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.sendChatMessage("What cryptographic proof forms are saved as evidence?")
                            }
                            .padding(8.dp)
                            .testTag("chat_preset_chip_2")
                    ) {
                        Text(
                            text = "Evidence Proofs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCABEFF)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF211F24), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF4F378B), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.sendChatMessage("How does Google Play Integrity API compliance function?")
                            }
                            .padding(8.dp)
                            .testTag("chat_preset_chip_3")
                    ) {
                        Text(
                            text = "Play Integrity",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCABEFF)
                        )
                    }
                }
            }

            // Input Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask Shield AI...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_text_field"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF27252C),
                        unfocusedContainerColor = Color(0xFF27252C),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color(0xFFE6E1E5),
                        unfocusedTextColor = Color(0xFFE6E1E5),
                        focusedPlaceholderColor = Color(0xFFE6E1E5).copy(alpha = 0.5f),
                        unfocusedPlaceholderColor = Color(0xFFE6E1E5).copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )

                IconButton(
                    onClick = {
                        if (inputText.trim().isNotEmpty()) {
                            viewModel.sendChatMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF6750A4), RoundedCornerShape(24.dp))
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Message",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
