package com.roadready.ui.screens.education

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.roadready.data.model.Quiz
import com.roadready.data.model.QuizQuestion
import com.roadready.data.remote.ApiClient
import com.roadready.ui.components.LoadingOverlay
import com.roadready.ui.components.PrimaryButton
import com.roadready.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun QuizScreen(
    moduleId: Int,
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val apiClient: ApiClient = koinInject()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var quiz by remember { mutableStateOf<Quiz?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var isAnswered by remember { mutableStateOf(false) }
    var correctCount by remember { mutableIntStateOf(0) }
    var showResults by remember { mutableStateOf(false) }

    LaunchedEffect(moduleId) {
        apiClient.getQuiz(moduleId).onSuccess { quiz = it }
        isLoading = false
    }

    if (isLoading) { LoadingOverlay(); return }
    if (quiz == null || quiz!!.questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📝", style = MaterialTheme.typography.displayLarge)
                Text("No quiz available for this module", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text("Go Back", color = Primary) }
            }
        }
        return
    }

    val questions = quiz!!.questions
    val totalQuestions = questions.size

    if (showResults) {
        QuizResults(
            correct = correctCount,
            total = totalQuestions,
            onRetry = {
                currentIndex = 0; correctCount = 0; showResults = false; selectedAnswer = null; isAnswered = false
            },
            onDone = {
                scope.launch {
                    apiClient.submitQuiz(moduleId, mapOf("score" to correctCount, "total" to totalQuestions))
                }
                onComplete()
            },
        )
        return
    }

    val question = questions[currentIndex]

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("✕ Exit", color = TextMuted) }
            Text("${currentIndex + 1} / $totalQuestions", style = MaterialTheme.typography.titleMedium)
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / totalQuestions },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            color = Primary,
            trackColor = SurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        // Question text
        Text(question.text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 24.dp))

        // Answer options
        question.options.forEachIndexed { index, option ->
            val isCorrect = index == question.correctIndex
            val isSelected = selectedAnswer == index
            val containerColor = when {
                !isAnswered -> if (isSelected) Primary.copy(alpha = 0.15f) else Surface
                isCorrect -> Accent.copy(alpha = 0.2f)
                isSelected -> Error.copy(alpha = 0.2f)
                else -> Surface
            }
            val borderColor = when {
                !isAnswered && isSelected -> Primary
                isAnswered && isCorrect -> Accent
                isAnswered && isSelected -> Error
                else -> SurfaceVariant
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = {
                    if (!isAnswered) {
                        selectedAnswer = index
                        isAnswered = true
                        if (isCorrect) correctCount++
                    }
                },
                colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${'A' + index}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) Primary else TextMuted,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(option, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (isAnswered) {
                        Text(if (isCorrect) "✓" else if (isSelected) "✗" else "",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isCorrect) Accent else Error)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (isAnswered) {
            PrimaryButton(
                text = if (currentIndex == totalQuestions - 1) "See Results" else "Next Question",
                onClick = {
                    if (currentIndex == totalQuestions - 1) {
                        showResults = true
                    } else {
                        currentIndex++
                        selectedAnswer = null
                        isAnswered = false
                    }
                },
            )
        }
    }
}

@Composable
private fun QuizResults(correct: Int, total: Int, onRetry: () -> Unit, onDone: () -> Unit) {
    val percentage = if (total > 0) (correct * 100 / total) else 0
    val passed = percentage >= 70

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (passed) "🎉" else "😔",
            style = MaterialTheme.typography.displayLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (passed) "Great Job!" else "Keep Practicing!",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "$correct / $total correct ($percentage%)",
            style = MaterialTheme.typography.titleLarge,
            color = if (passed) Accent else Warning,
        )
        Spacer(Modifier.height(32.dp))

        PrimaryButton(text = if (passed) "Continue" else "Try Again",
            onClick = if (passed) onDone else onRetry,
            color = if (passed) Accent else Primary)
        if (!passed) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDone) { Text("Skip for now", color = TextMuted) }
        }
    }
}
