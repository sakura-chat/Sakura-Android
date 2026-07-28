package dev.kuylar.sakura.mscexplorer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullRequestItem(
	val url: String,
	val id: Long,

	@SerialName("node_id")
	val nodeId: String,

	@SerialName("html_url")
	val htmlUrl: String,

	@SerialName("diff_url")
	val diffUrl: String,

	@SerialName("patch_url")
	val patchUrl: String,

	@SerialName("issue_url")
	val issueUrl: String,

	val number: Int,
	val state: String,
	val locked: Boolean,
	val title: String,
	val user: User,
	val body: String,

	@SerialName("created_at")
	val createdAt: String,

	@SerialName("updated_at")
	val updatedAt: String,

	@SerialName("closed_at")
	val closedAt: String? = null,

	@SerialName("merged_at")
	val mergedAt: String? = null,

	@SerialName("merge_commit_sha")
	val mergeCommitSha: String,

	@SerialName("requested_reviewers")
	val requestedReviewers: List<User>,

	val labels: List<Label>,
	val draft: Boolean,

	@SerialName("commits_url")
	val commitsUrl: String,

	@SerialName("review_comments_url")
	val reviewCommentsUrl: String,

	@SerialName("review_comment_url")
	val reviewCommentUrl: String,

	@SerialName("comments_url")
	val commentsUrl: String,

	@SerialName("statuses_url")
	val statusesUrl: String,

	@SerialName("author_association")
	val authorAssociation: String,
)

@Serializable
data class User(
	val login: String,
	val id: Long,

	@SerialName("node_id")
	val nodeId: String,

	@SerialName("avatar_url")
	val avatarUrl: String,

	@SerialName("gravatar_id")
	val gravatarId: String,

	val url: String,

	val type: String
)

@Serializable
data class Label(
	val id: Long,

	@SerialName("node_id")
	val nodeId: String,

	val url: String,
	val name: String,
	val color: String,
	val default: Boolean,
	val description: String
)