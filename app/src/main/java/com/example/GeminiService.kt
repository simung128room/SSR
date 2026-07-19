package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: JsonObject? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

// Define local results to parse
@Serializable
data class AutoSuggestResult(
    val energyRequired: String,
    val category: String,
    val isImportant: Boolean = false
)

@Serializable
data class SubTaskSuggestion(
    val title: String,
    val energyRequired: String,
    val category: String,
    val durationMinutes: Int
)

@Serializable
data class TaskBreakdownResult(
    val subtasks: List<SubTaskSuggestion>
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiService {
    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    suspend fun autoSuggestTask(title: String): AutoSuggestResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) return@withContext null

        val prompt = "Evaluate the task title: \"$title\". Suggest the energy level required (LOW, MEDIUM, or HIGH), whether it is important (true or false), and the best matching category from this list: [\"ทั่วไป\", \"สอบ\", \"เรียน\", \"เนื้อหา\", \"อ่านหนังสือ\"]."
        
        val schema = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("energyRequired") {
                    put("type", "STRING")
                    put("description", "LOW, MEDIUM, or HIGH")
                }
                putJsonObject("category") {
                    put("type", "STRING")
                    put("description", "One of: ทั่วไป, สอบ, เรียน, เนื้อหา, อ่านหนังสือ")
                }
                putJsonObject("isImportant") {
                    put("type", "BOOLEAN")
                }
            }
            put("required", kotlinx.serialization.json.JsonArray(listOf(
                kotlinx.serialization.json.JsonPrimitive("energyRequired"),
                kotlinx.serialization.json.JsonPrimitive("category"),
                kotlinx.serialization.json.JsonPrimitive("isImportant")
            )))
        }

        val requestBody = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.2f
            ),
            systemInstruction = Content(parts = listOf(Part(text = "You are a smart academic task organizer. Categorize student tasks and evaluate details accurately.")))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, requestBody)
            val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                jsonDecoder.decodeFromString<AutoSuggestResult>(jsonText)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun breakdownTask(title: String): List<SubTaskSuggestion>? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) return@withContext null

        val prompt = "Break down the main task: \"$title\" into 3 to 5 smaller, manageable, actionable steps. For each subtask, suggest a specific, clear title in Thai, an energy level (LOW, MEDIUM, or HIGH), a category matching [\"ทั่วไป\", \"สอบ\", \"เรียน\", \"เนื้อหา\", \"อ่านหนังสือ\"], and estimated duration in minutes."

        val schema = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("subtasks") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("title") {
                                put("type", "STRING")
                                put("description", "Actionable subtask title in Thai")
                            }
                            putJsonObject("energyRequired") {
                                put("type", "STRING")
                                put("description", "LOW, MEDIUM, or HIGH")
                            }
                            putJsonObject("category") {
                                put("type", "STRING")
                                put("description", "One of: ทั่วไป, สอบ, เรียน, เนื้อหา, อ่านหนังสือ")
                            }
                            putJsonObject("durationMinutes") {
                                put("type", "INTEGER")
                                put("description", "Duration in minutes (e.g. 15, 30, 45, 60)")
                            }
                        }
                        put("required", kotlinx.serialization.json.JsonArray(listOf(
                            kotlinx.serialization.json.JsonPrimitive("title"),
                            kotlinx.serialization.json.JsonPrimitive("energyRequired"),
                            kotlinx.serialization.json.JsonPrimitive("category"),
                            kotlinx.serialization.json.JsonPrimitive("durationMinutes")
                        )))
                    }
                }
            }
            put("required", kotlinx.serialization.json.JsonArray(listOf(
                kotlinx.serialization.json.JsonPrimitive("subtasks")
            )))
        }

        val requestBody = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.4f
            ),
            systemInstruction = Content(parts = listOf(Part(text = "You are a professional tutor and academic coach. Break down complex study topics or student assignments into highly practical, bite-sized tasks.")))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, requestBody)
            val jsonText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                jsonDecoder.decodeFromString<TaskBreakdownResult>(jsonText).subtasks
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getDailyBriefing(tasks: List<TaskEntity>, userEnergy: EnergyLevel): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) return@withContext "กรุณาใส่ API Key ในส่วนตั้งค่าของระบบเพื่อใช้ฟีเจอร์ผู้ช่วย AI"

        val taskDescriptions = tasks.joinToString("\n") { task ->
            val status = if (task.isCompleted) "เสร็จแล้ว" else "ยังไม่เสร็จ"
            val time = if (task.startTimeMs != null) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = task.startTimeMs }
                String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            } else "ไม่ระบุเวลา"
            "- ${task.title} (หมวดหมู่: ${task.category}, ระดับพลังงาน: ${task.energyRequired}, เวลา: $time, สถานะ: $status)"
        }

        val prompt = """
            ระดับพลังงานที่เลือกวันนี้: $userEnergy
            
            รายการงานวันนี้:
            $taskDescriptions
            
            กรุณาสรุปภาพรวมและแนะนำการใช้ชีวิตในวันนี้เป็นภาษาไทยที่กระชับ อบอุ่น และสร้างแรงบันดาลใจ ให้เน้นการจับคู่ระดับพลังงานของผู้ใช้งานกับการทำกิจกรรม และหากงานชิ้นไหนสำคัญควรเตือนเป็นพิเศษ ให้คำแนะนำสั้นๆ ไม่เกิน 3-4 บรรทัด.
        """.trimIndent()

        val requestBody = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.7f),
            systemInstruction = Content(parts = listOf(Part(text = "You are a friendly, caring academic mentor. Write in comforting and motivational Thai. Keep your advice short, concise, and deeply practical.")))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, requestBody)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "ไม่สามารถดึงข้อมูลสรุปจาก AI ได้ในขณะนี้"
        } catch (e: Exception) {
            "เกิดข้อผิดพลาดในการเชื่อมต่อกับผู้ช่วย AI: ${e.message}"
        }
    }

    suspend fun analyzeTaskImage(title: String, imagePath: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty()) return@withContext "กรุณาตั้งค่า API Key ในระบบเพื่อใช้งานฟีเจอร์นี้"

        val base64Image = try {
            val file = java.io.File(imagePath)
            if (!file.exists()) return@withContext "ไม่พบไฟล์รูปภาพที่บันทึกไว้ในอุปกรณ์"

            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(imagePath, options)

            var scale = 1
            while (options.outWidth / scale / 2 >= 1024 || options.outHeight / scale / 2 >= 1024) {
                scale *= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = scale
            }
            val bitmap = android.graphics.BitmapFactory.decodeFile(imagePath, decodeOptions)
                ?: return@withContext "ไม่สามารถดึงรูปภาพขึ้นมาวิเคราะห์ได้"

            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "เกิดข้อผิดพลาดในการดึงและเข้ารหัสรูปภาพ: ${e.localizedMessage}"
        }

        val prompt = "รูปนี้คือภาพประกอบของงานที่มีหัวข้อว่า: \"$title\"\n\nโปรดทำการวิเคราะห์รูปภาพนี้เพื่อค้นหาเฉลย คำอธิบายอย่างละเอียด วิธีคิด วิธีคำนวณ หรือเนื้อหาทางวิชาการที่เกี่ยวข้องทั้งหมดให้เข้าใจง่ายเป็นภาษาไทย โดยจัดทำคำตอบออกมาเป็นหัวข้อย่อยและขั้นตอนที่ชัดเจน เพื่อให้ฉันสามารถใช้ทบทวนเพื่อการเรียนและการสอบได้ดีที่สุด"

        val requestBody = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(temperature = 0.4f),
            systemInstruction = Content(
                parts = listOf(
                    Part(text = "You are a professional academic mentor and expert tutor. Analyze the given question, task, problem, or diagram from the image. Provide a highly structured step-by-step solution, answer, explanation, and conceptual tutoring points in Thai. Use markdown, lists, and bold text for superb readability. If the image is unclear, state your assumptions and explain the underlying concepts clearly.")
                )
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, requestBody)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "ไม่สามารถประมวลผลคำตอบได้จาก AI กรุณาลองอีกครั้ง"
        } catch (e: Exception) {
            e.printStackTrace()
            "เกิดข้อผิดพลาดในการเชื่อมต่อเพื่อวิเคราะห์ด้วย AI: ${e.localizedMessage}"
        }
    }
}
