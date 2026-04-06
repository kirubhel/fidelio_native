@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package world.respect.kokebfidel

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import world.respect.kokebfidel.data.mock.GameData
import world.respect.kokebfidel.data.models.Activity
import world.respect.kokebfidel.data.models.Game
import world.respect.kokebfidel.data.models.RespectLaunchInfo
import androidx.lifecycle.ViewModelProvider
import world.respect.kokebfidel.ui.GameViewModel
import world.respect.kokebfidel.ui.GameViewModelFactory
import android.speech.tts.TextToSpeech
import java.util.Locale
import world.respect.kokebfidel.data.utils.toDomain
import world.respect.kokebfidel.data.repository.GameRepository

class MainActivity : ComponentActivity() {
    private val TAG = "RESPECT_LAUNCH"
    private val respectLaunchInfo = MutableStateFlow<RespectLaunchInfo?>(null)
    private lateinit var gameViewModel: GameViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        val repository = world.respect.kokebfidel.data.repository.GameRepository(applicationContext)
        val factory = world.respect.kokebfidel.ui.GameViewModelFactory(repository)
        gameViewModel = ViewModelProvider(this, factory)[world.respect.kokebfidel.ui.GameViewModel::class.java]
        
        handleIntent(intent)
        
        setContent {

            val navController = rememberNavController()
            val respectInfo by respectLaunchInfo.collectAsState()
            val gamesByEntity by gameViewModel.games.collectAsState()
            val games = gamesByEntity.map { it.toDomain() }.filter { it.isActive }
            val isLoading by gameViewModel.loading.collectAsState()
            
            NavHost(navController = navController, startDestination = "splash") {
                composable("splash") { 
                    SplashScreen(onFinished = { navController.navigate("home") { popUpTo("splash") { inclusive = true } } }) 
                }
                composable("home") { 
                    HomeMapScreen(respectInfo, games, isLoading, onGameSelected = { game -> navController.navigate("levels/${game.id}") }, onRefresh = { gameViewModel.refreshGames() }) 
                }
                composable("levels/{gameId}") { backStackEntry ->
                    val gameId = backStackEntry.arguments?.getString("gameId")
                    LaunchedEffect(gameId) { gameId?.let { gameViewModel.loadGameDetails(it) } }
                    val game = games.find { it.id == gameId }
                    if (game != null) {
                        val progress by gameViewModel.progress.collectAsState()
                        LevelSelectionScreen(
                            game = game, 
                            isLoading = isLoading, 
                            progress = progress,
                            onRefresh = { gameId?.let { gameViewModel.loadGameDetails(it) } },
                            onActivitySelected = { a, mode, idx -> 
                                navController.navigate("game/${game.id}/$mode/$idx") 
                            }, 
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable("game/{gameId}/{mode}/{index}") { backStackEntry ->
                    val gameId = backStackEntry.arguments?.getString("gameId")
                    val mode = backStackEntry.arguments?.getString("mode")
                    val index = backStackEntry.arguments?.getString("index")?.toIntOrNull() ?: 0
                    val game = games.find { it.id == gameId }
                    if (game != null) {
                        GameScreen(navController, game, mode ?: "easy", index, respectInfo, onBack = { navController.popBackStack() }, onMissionCompleted = { aid, s ->
                            gameViewModel.saveStars(game.id, game.title, aid, s, launchInfo = respectInfo)
                        })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.getQueryParameter("respectLaunchVersion") == "1") {
            respectLaunchInfo.value = RespectLaunchInfo(
                auth = uri.getQueryParameter("auth"),
                xapiEndpoint = uri.getQueryParameter("endpoint"),
                givenName = uri.getQueryParameter("given_name"),
                activityId = uri.getQueryParameter("activity_id")
            )
        }
    }
}

data class RespectLaunchInfo(val auth: String?, val xapiEndpoint: String?, val givenName: String?, val activityId: String?)

// --- SCREENS ---

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var stage by remember { mutableStateOf(1) }
    
    LaunchedEffect(Unit) {
        delay(1500)
        stage = 2
        delay(1500)
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.7f),
            contentScale = ContentScale.Crop
        )
        
        AnimatedContent(targetState = stage, label = "splash") { currentStage ->
            Image(
                painter = painterResource(id = if (currentStage == 1) R.drawable.logo_splash else R.drawable.logo),
                contentDescription = "Logo",
                modifier = Modifier.width(200.dp)
            )
        }
    }
}

@Composable
fun HomeMapScreen(respectInfo: RespectLaunchInfo?, games: List<Game>, isLoading: Boolean, onGameSelected: (Game) -> Unit, onRefresh: () -> Unit) {
    val luckiestGuy = FontFamily(Font(R.font.luckiest_guy))
    val fredoka = FontFamily(Font(R.font.fredoka_bold))
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Soft Blue Gradient BG
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF8ECAFE), Color(0xFFCDE9FE)))
        ))
        
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.3f),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header: All Games
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 12.dp, start = 24.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(0.4f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SportsEsports, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text("All Games", fontFamily = luckiestGuy, fontSize = 32.sp, color = Color.White)
                Text(" 🎮", fontSize = 28.sp)
            }

            // Search & Filter Bar
            var searchQuery by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
            val filteredGames = if (searchQuery.isEmpty()) games else games.filter { 
                it.title.contains(searchQuery, ignoreCase = true) || 
                it.subject.contains(searchQuery, ignoreCase = true)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Input (Replacing Filter Games)
                Surface(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        Row(modifier = Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = fredoka, color = Color(0xFF334155), fontSize = 16.sp),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text("Filter Games...", color = Color(0xFF94A3B8), fontFamily = fredoka, fontSize = 16.sp)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }
                }
                
                // Action Icon
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF3B82F6),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Tune, null, tint = Color.White)
                    }
                }
            }

            // Games Scroll Area with Pull to Refresh
            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (filteredGames.isNotEmpty()) {
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            contentPadding = PaddingValues(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Hero Mission (only if search is empty to maintain layout)
                            if (searchQuery.isEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                    GameCard(filteredGames[0], onSelected = { onGameSelected(filteredGames[0]) }, isFeatured = true)
                                }
                                items(filteredGames.size - 1) { index ->
                                    GameCard(filteredGames[index + 1], onSelected = { onGameSelected(filteredGames[index + 1]) }, isFeatured = false)
                                }
                            } else {
                                // Show all result in grid
                                items(filteredGames.size) { index ->
                                    GameCard(filteredGames[index], onSelected = { onGameSelected(filteredGames[index]) }, isFeatured = false)
                                }
                            }
                        }
                    } else if (!isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔍", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No missions found.", color = Color.White.copy(0.7f), fontFamily = luckiestGuy)
                            }
                        }
                    }
                }
            }

            // Bottom Navigation (Screenshot Match)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .height(84.dp),
                shape = RoundedCornerShape(32.dp),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavItem("Play", Icons.Default.SportsEsports, true)
                }
            }
        }
    }
}

@Composable
fun BottomNavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isActive: Boolean) {
    val fredoka = FontFamily(Font(R.font.fredoka_bold))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (isActive) 52.dp else 32.dp)
                .background(if (isActive) Color(0xFFCDE9FE) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, 
                contentDescription = label, 
                tint = if (isActive) Color(0xFF3B82F6) else Color(0xFF94A3B8),
                modifier = Modifier.size(if(isActive) 28.dp else 24.dp)
            )
            if (isActive && label == "Trace") {
                // Lock badge
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFFFFD600), modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontFamily = fredoka, fontSize = 10.sp, color = if(isActive) Color(0xFF3B82F6) else Color(0xFF94A3B8))
    }
}

@Composable
fun GameCard(game: Game, onSelected: () -> Unit, isFeatured: Boolean) {
    val luckiestGuy = FontFamily(Font(R.font.luckiest_guy))
    val fredoka = FontFamily(Font(R.font.fredoka_bold))
    
    Card(
        onClick = onSelected,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isFeatured) 220.dp else 180.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // FIX: Prioritize remote thumbnail_url
            val thumbnailPath = if (game.thumbnailUrl.startsWith("http")) {
                game.thumbnailUrl
            } else {
                "file:///android_asset/${game.thumbnailUrl.replace("assets/", "")}"
            }
            
            AsyncImage(
                model = thumbnailPath,
                contentDescription = game.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Soft overlay for text readability
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                    startY = if(isFeatured) 300f else 200f
                )
            ))

            // Score Badge
            val totalStars = (game.easyActivities + game.mediumActivities + game.hardActivities).sumOf { it.stars }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(0.9f)
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$totalStars", fontFamily = fredoka, fontSize = 12.sp, color = Color(0xFF5D4037))
                }
            }
            
            if (isFeatured) {
                // Secondary info for Hero card
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(game.title, color = Color.White, fontFamily = luckiestGuy, fontSize = 24.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, null, tint = Color(0xFFFFCA28), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Best: 0", color = Color.White.copy(0.9f), fontSize = 14.sp, fontFamily = fredoka)
                    }
                }
            } else {
                Text(
                    game.title, 
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    color = Color.White, 
                    fontFamily = luckiestGuy, 
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LevelSelectionScreen(game: Game, isLoading: Boolean, progress: Map<String, Int>, onRefresh: () -> Unit, onActivitySelected: (Activity, String, Int) -> Unit, onBack: () -> Unit) {
    val luckiestGuy = FontFamily(Font(R.font.luckiest_guy))
    
    val allFlattened = game.easyActivities + game.mediumActivities + game.hardActivities
    val unlockedIndices = mutableSetOf<Int>()
    unlockedIndices.add(0)
    for (i in 0 until allFlattened.size - 1) {
        val currentStars = progress[allFlattened[i].id] ?: 0
        if (currentStars >= 2) unlockedIndices.add(i + 1)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFCDE9FE))) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.3f),
            contentScale = ContentScale.Crop
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(game.title, fontFamily = luckiestGuy, fontSize = 28.sp, color = Color(0xFF3B82F6))
            }

            Box(modifier = Modifier.weight(1f)) {
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = isLoading,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val allFlattened = game.easyActivities + game.mediumActivities + game.hardActivities
                        val unlockedIndices = mutableSetOf<Int>()
                        unlockedIndices.add(0)
                        for (i in 0 until allFlattened.size - 1) {
                            if (allFlattened[i].stars >= 2) unlockedIndices.add(i + 1)
                        }

                        DifficultySection("Easy", game.easyActivities, 0, unlockedIndices, progress, Color(0xFF4CAF50), onActivitySelected)
                        
                        if (game.easyActivities.isNotEmpty() && game.mediumActivities.isNotEmpty()) {
                            LevelLadder()
                        }
                        
                        DifficultySection("Medium", game.mediumActivities, game.easyActivities.size, unlockedIndices, progress, Color(0xFFFF9800), onActivitySelected)
                        
                        if (game.mediumActivities.isNotEmpty() && game.hardActivities.isNotEmpty()) {
                            LevelLadder()
                        }
                        
                        DifficultySection("Hard", game.hardActivities, game.easyActivities.size + game.mediumActivities.size, unlockedIndices, progress, Color(0xFFF44336), onActivitySelected)
                        
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DifficultySection(name: String, activities: List<Activity>, globalStartIndex: Int, unlockedIndices: Set<Int>, progress: Map<String, Int>, color: Color, onSelected: (Activity, String, Int) -> Unit) {
    val luckiestGuy = FontFamily(Font(R.font.luckiest_guy))
    val fredoka = FontFamily(Font(R.font.fredoka_bold))

    if (activities.isEmpty()) return

    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.9f)),
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Section Header Badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = color,
                modifier = Modifier.padding(bottom = 24.dp).align(Alignment.Start)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    val completedCount = activities.count { (progress[it.id] ?: 0) > 0 }
                    Text("$completedCount/${activities.size} $name", color = Color.White, fontFamily = fredoka, fontSize = 14.sp)
                }
            }

            // Zigzag Grid Logic (3 Columns)
            val rows = activities.chunked(3)
            rows.forEachIndexed { rowIndex, rowActivities ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = if (rowIndex % 2 == 0) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayedActivities = if (rowIndex % 2 == 1) rowActivities.reversed() else rowActivities
                    
                    displayedActivities.forEachIndexed { nodeIndex, activity ->
                        val globalIndex = rowIndex * 3 + (if (rowIndex % 2 == 1) rowActivities.size - 1 - nodeIndex else nodeIndex)
                        val absoluteIndex = globalStartIndex + globalIndex
                        val isLocked = !unlockedIndices.contains(absoluteIndex)

                        MissionNode(
                            index = absoluteIndex + 1,
                            color = color,
                            stars = progress[activity.id] ?: 0,
                            isLocked = isLocked,
                            onClick = { if (!isLocked) onSelected(activity, name.lowercase(), absoluteIndex) }
                        )

                        // Horizontal path between nodes
                        if (nodeIndex < displayedActivities.size - 1) {
                            Box(modifier = Modifier.width(24.dp).height(8.dp).background(color.copy(0.6f), RoundedCornerShape(4.dp)))
                        }
                    }
                }
                
                // Vertical path connecting rows
                if (rowIndex < rows.size - 1) {
                    val isEndAtRight = rowIndex % 2 == 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        contentAlignment = if (isEndAtRight) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(modifier = Modifier.width(8.dp).height(24.dp).background(color.copy(0.6f), RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
}

@Composable
fun MissionNode(index: Int, color: Color, stars: Int, isLocked: Boolean, onClick: () -> Unit) {
    val luckiestGuy = FontFamily(Font(R.font.luckiest_guy))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
        Box(modifier = Modifier.size(64.dp).shadow(8.dp, CircleShape).background(Color.White, CircleShape).border(4.dp, if(isLocked) Color.LightGray.copy(0.4f) else color.copy(0.4f), CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(52.dp).background(if(isLocked) Color.LightGray.copy(0.5f) else if(stars > 0) color else color.copy(0.6f), CircleShape), contentAlignment = Alignment.Center) {
                if (isLocked) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("$index", color = Color.White, fontFamily = luckiestGuy, fontSize = 24.sp)
                }
            }
        }
        
        // Progress Stars
        Row(modifier = Modifier.padding(top = 4.dp)) {
            repeat(3) { i ->
                Icon(Icons.Default.Star, null, tint = if(i < stars) Color(0xFFFFD600) else Color.LightGray.copy(0.5f), modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
fun LevelLadder() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(12.dp)
                    .border(2.dp, Color(0xFF5D4037), RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
fun ConfettiCelebration() {
    val particles = androidx.compose.runtime.remember { List(30) { Math.random() to Math.random() } }
    val infiniteTransition = rememberInfiniteTransition()
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        particles.forEach { (x, delay) ->
            Box(
                modifier = Modifier
                    .offset(x = (x * 400).toInt().dp, y = (offsetY * (1 + delay.toFloat())).dp)
                    .size(if (delay > 0.5) 8.dp else 12.dp)
                    .background(
                        color = listOf(Color.Red, Color.Yellow, Color.Blue, Color.Green, Color.Magenta).random(),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun GameScreen(navController: NavController, game: Game, mode: String, index: Int, launchInfo: world.respect.kokebfidel.data.models.RespectLaunchInfo?, onBack: () -> Unit, onMissionCompleted: (String, Int) -> Unit) {
    val activities = when(mode) {
        "easy" -> game.easyActivities
        "medium" -> game.mediumActivities
        else -> game.hardActivities
    }
    
    fun getInitialTime() = when(mode) {
        "easy" -> 180
        "medium" -> 120
        else -> 60
    }

    var currentIndex by remember { mutableStateOf(index) }
    val activity = activities.getOrNull(currentIndex) ?: return
    
    var timeSeconds by remember(currentIndex) { mutableStateOf(getInitialTime()) }
    var userInput by remember(currentIndex) { mutableStateOf(List(activity.letters.size) { "" }) }
    var shuffledLetters by remember(currentIndex) { mutableStateOf(activity.letters.shuffled()) }
    
    // Mechanics State
    var stars by remember(currentIndex) { mutableStateOf(3) }
    var hintUsed by remember(currentIndex) { mutableStateOf(false) }
    
    var showInstruction by remember { mutableStateOf(true) }
    var showSuccess by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(0) }
    
    val fredoka = FontFamily(Font(R.font.fredoka_bold))
    val luckiestGuy = FontFamily(Font(R.font.luckiest_guy))

    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    
    DisposableEffect(Unit) {
        val ttsInstance = TextToSpeech(context) { status -> }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    fun playAudio(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    LaunchedEffect(timeSeconds, showInstruction, countdown, showSuccess) {
        if (!showInstruction && countdown == 0 && !showSuccess && timeSeconds > 0) {
            delay(1000)
            timeSeconds -= 1
        }
    }
    
    LaunchedEffect(showInstruction) {
        if (!showInstruction) {
            countdown = 3
            while(countdown > 0) {
                delay(1000)
                countdown -= 1
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFCDE9FE))) {
        Image(painter = painterResource(id = R.drawable.background), contentDescription = null, modifier = Modifier.fillMaxSize().alpha(0.7f), contentScale = ContentScale.Crop)

        if (showSuccess) ConfettiCelebration()

        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.background(Color(0xFF2563EB), RoundedCornerShape(12.dp)).size(48.dp)) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
                }

                Row(modifier = Modifier.background(Color.White.copy(0.5f), RoundedCornerShape(24.dp)).border(2.dp, Color.White, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${timeSeconds / 60}:${(timeSeconds % 60).toString().padStart(2, '0')}", fontFamily = luckiestGuy, color = Color(0xFF2563EB), fontSize = 18.sp)
                }

                Row {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { 
                            stars = maxOf(1, stars - 1)
                            timeSeconds = getInitialTime()
                            userInput = List(activity.letters.size) { "" }
                            shuffledLetters = activity.letters.shuffled()
                        }, modifier = Modifier.background(Color.White.copy(0.5f), RoundedCornerShape(8.dp)).size(40.dp)) {
                            Icon(Icons.Default.Refresh, "Restart", tint = Color(0xFF2563EB))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            enabled = !hintUsed,
                            onClick = { 
                                hintUsed = true
                                stars = maxOf(1, stars - 1)
                                val emptyIdx = userInput.indexOfFirst { it.isEmpty() }
                                if (emptyIdx != -1) {
                                    val correctChar = activity.letters[emptyIdx]
                                    val shuffledIdx = shuffledLetters.indexOf(correctChar)
                                    if (shuffledIdx != -1) {
                                        val newUserInput = userInput.toMutableList()
                                        newUserInput[emptyIdx] = correctChar
                                        val newShuffled = shuffledLetters.toMutableList()
                                        newShuffled.removeAt(shuffledIdx)
                                        userInput = newUserInput
                                        shuffledLetters = newShuffled
                                        if(newUserInput.joinToString("") == activity.letters.joinToString("")) {
                                            showSuccess = true
                                            onMissionCompleted(activity.id, stars)
                                        }
                                    }
                                }
                            }, 
                            modifier = Modifier.background(if(hintUsed) Color.Gray.copy(0.3f) else Color.White.copy(0.5f), RoundedCornerShape(8.dp)).size(40.dp)
                        ) {
                            Icon(Icons.Default.Lightbulb, "Hint", tint = if(hintUsed) Color.Gray else Color(0xFFF59E0B))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Missions Progress Header
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).background(Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF3B82F6))), RoundedCornerShape(24.dp)).padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(game.title, fontFamily = luckiestGuy, color = Color.White, fontSize = 20.sp)
                    Row {
                        repeat(3) { i ->
                            Icon(Icons.Default.Star, null, tint = if(i < stars) Color(0xFFFFD600) else Color.White.copy(0.3f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Text("${currentIndex + 1}/${activities.size}", fontFamily = fredoka, color = Color.White, modifier = Modifier.background(Color.White.copy(0.2f), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
            }

            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Mission Image Card
                Box(modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(32.dp)).clickable { playAudio(activity.word) }.border(4.dp, Color.White, RoundedCornerShape(32.dp)).shadow(12.dp, RoundedCornerShape(32.dp))) {
                    val activityImageModel = if (activity.imageUrl.startsWith("http")) activity.imageUrl else "file:///android_asset/${activity.imageUrl.replace("assets/", "")}"
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(activityImageModel)
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(), 
                        contentDescription = null, 
                        modifier = Modifier.width(300.dp).height(240.dp).background(Color.White), 
                        contentScale = ContentScale.Crop
                    )
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp).background(Color.White.copy(0.9f), CircleShape).border(3.dp, Color(0xFF60A5FA), CircleShape).clickable { playAudio(activity.word) }.padding(12.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VolumeUp, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Input Boxes
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    userInput.forEachIndexed { idx, letter ->
                        val isEmpty = letter.isEmpty()
                        Box(modifier = Modifier.padding(4.dp).size(60.dp).background(if (isEmpty) Color.White.copy(0.5f) else Color(0xFF3B82F6), RoundedCornerShape(12.dp)).border(2.dp, if(isEmpty) Color(0xFF3B82F6) else Color.Transparent, RoundedCornerShape(12.dp)).clickable {
                            if (!isEmpty) {
                                val newUserInput = userInput.toMutableList()
                                newUserInput[idx] = ""
                                userInput = newUserInput
                                shuffledLetters = (shuffledLetters + letter).shuffled()
                            }
                        }, contentAlignment = Alignment.Center) {
                            Text(letter, fontFamily = fredoka, color = Color.White, fontSize = 28.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // Letter Bank
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 70.dp)) {
                    shuffledLetters.forEachIndexed { idx, letter ->
                        Box(modifier = Modifier.padding(4.dp).size(60.dp).background(Brush.linearGradient(listOf(Color(0xFFFF9052), Color(0xFFFFB660))), RoundedCornerShape(12.dp)).clickable {
                            val emptyIndex = userInput.indexOfFirst { it.isEmpty() }
                            if(emptyIndex != -1) {
                                val newUserInput = userInput.toMutableList()
                                newUserInput[emptyIndex] = letter
                                val newShuffled = shuffledLetters.toMutableList()
                                newShuffled.removeAt(idx)
                                userInput = newUserInput
                                shuffledLetters = newShuffled
                                if(newUserInput.joinToString("") == activity.letters.joinToString("")) showSuccess = true
                            }
                        }, contentAlignment = Alignment.Center) {
                            Text(letter, fontFamily = fredoka, color = Color.White, fontSize = 28.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Overlay Components (Instructions, Countdown, Success)
        if (showInstruction) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable{}, contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.background(Color(0xFFFFF9ED), RoundedCornerShape(24.dp)).padding(24.dp).width(300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).background(Color(0xFF3B82F6), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.School, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("How to play! \uD83C\uDFAE", fontFamily = fredoka, fontSize = 20.sp, color = Color(0xFF2563EB))
                    Spacer(modifier = Modifier.height(24.dp))
                    InstructionRow("1", "Look at the picture", Icons.Default.Image)
                    InstructionRow("2", "Arrange the letters", Icons.Default.TextFields)
                    InstructionRow("3", "Win Stars! \u2B50", Icons.Default.EmojiEvents)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { showInstruction = false }, modifier = Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))) {
                        Text("Ready!", fontFamily = fredoka, fontSize = 20.sp)
                    }
                }
            }
        } else if (countdown > 0) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.6f)), contentAlignment = Alignment.Center) {
                Text("$countdown", fontFamily = luckiestGuy, fontSize = 160.sp, color = Color.White)
            }
        } else if (showSuccess) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).clickable{}, contentAlignment = Alignment.Center) {
                Column(modifier = Modifier.background(Color.White, RoundedCornerShape(24.dp)).padding(32.dp).width(300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        repeat(3) { i ->
                            Icon(Icons.Default.Star, null, tint = if(i < stars) Color(0xFFFFC107) else Color.LightGray, modifier = Modifier.size(if(i==1) 80.dp else 60.dp).offset(y = if(i==1) (-10).dp else 0.dp))
                        }
                    }
                    Text("Excellent!", fontFamily = fredoka, fontSize = 24.sp, color = Color(0xFF3B82F6))
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { 
                        onMissionCompleted(activity.id, stars)
                        if (currentIndex < activities.size - 1) {
                            currentIndex++
                            timeSeconds = getInitialTime()
                            userInput = List(activities[currentIndex].letters.size) { "" }
                            shuffledLetters = activities[currentIndex].letters.shuffled()
                        } else { onBack() }
                        showSuccess = false 
                    }, modifier = Modifier.height(50.dp).fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA))) {
                        Text("Next Level", fontFamily = fredoka, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InstructionRow(step: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(30.dp).background(Color(0xFF60A5FA), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text(step, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(desc, modifier = Modifier.weight(1f), fontFamily = FontFamily(Font(R.font.fredoka_regular)), fontSize = 14.sp)
        Icon(icon, contentDescription = null, tint = Color(0xFF60A5FA))
    }
}
