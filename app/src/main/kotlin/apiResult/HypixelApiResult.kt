package apiResult

import com.google.gson.annotations.SerializedName

data class HypixelApiResult(
	val success: Boolean,
	val player: Player?
) : ApiResult {
	data class Player(
		val uuid: String,
		val socialMedia: SocialMedia?
	) {
		data class SocialMedia(
			val links: Links?
		) {
			data class Links(
				@SerializedName("DISCORD")
				val discord: String?
			)
		}
	}
}