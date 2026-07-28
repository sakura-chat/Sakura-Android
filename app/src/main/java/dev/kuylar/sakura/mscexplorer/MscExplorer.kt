package dev.kuylar.sakura.mscexplorer

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object MscExplorer {
	private val client = HttpClient {
		install(ContentNegotiation) {
			json(Json {
				ignoreUnknownKeys = true
			})
		}
	}

	suspend fun list(page: Int): List<MscExplorerItem> {
		val pulls =
			client.get("https://api.github.com/repos/matrix-org/matrix-spec-proposals/pulls?page=$page")
				.body<List<PullRequestItem>>()
		return pulls
			.filter {
				Regex("MSC\\d+:").containsMatchIn(it.title)
			}
			.mapNotNull { pull ->
				val body =
					Regex("\\[Rendered]\\((.+?\\.md)\\)").find(pull.body) ?: return@mapNotNull null
				MscExplorerItem(
					num = pull.number,
					title = pull.title,
					body = pull.body.replace(body.value, "").trim().split("\n")
						.filter { !it.startsWith("Signed-off-by:") }.joinToString("\n")
						.takeIf { it.isNotBlank() } ?: "<empty body>",
					spec = body.groupValues[1],
					user = "@" + pull.user.login,
					state = pull.state,
					labels = pull.labels.map { it.name })
			}
	}

	suspend fun getSpecDoc(url: String): String {
		return client.get(
			url.replace("/www.github.com/", "/raw.githubusercontent.com/")
				.replace("/github.com/", "/raw.githubusercontent.com/")
				.replace("/blob/", "/refs/heads/")
		).body<String>()
	}
}