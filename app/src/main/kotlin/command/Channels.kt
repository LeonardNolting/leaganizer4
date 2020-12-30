package command

import Config.Known
import bot
import com.jessecorbett.diskord.api.model.Channel
import com.jessecorbett.diskord.api.rest.CreateDM
import com.jessecorbett.diskord.api.rest.client.ChannelClient

interface BotChannel {
	/**
	 * userId: DM channel recognition
	 */
	suspend fun fine(channelId: String, userId: String): Boolean
	val ids: List<String>

	object Private : BotChannel {
		override suspend fun fine(channelId: String, userId: String) =
			bot.clientStore.discord.createDM(CreateDM(userId)).guildId == null

		override val ids = emptyList<String>()
	}

	class Reversed(
		private val botChannel: BotChannel
	) : BotChannel {
		override suspend fun fine(channelId: String, userId: String) = !botChannel.fine(channelId, userId)
		override val ids = emptyList<String>()
	}

	class Collection(
		private val botChannels: List<BotChannel>
	) : BotChannel {
		override suspend fun fine(channelId: String, userId: String) = botChannels.any { it.fine(channelId, userId) }
		override val ids = botChannels.flatMap { it.ids }
	}
}

infix fun BotChannel.or(other: BotChannel) = BotChannel.Collection(listOf(this, other))
fun not(botChannel: BotChannel) = BotChannel.Reversed(botChannel)

enum class Channels(private val botChannel: BotChannel? = null) : BotChannel {
	ANY,
	PRIVATE(BotChannel.Private),
	PUBLIC(not(PRIVATE)),
	REFS(Known.Channel.REFS),
	TRUSTED(Known.Channel.TRUSTED),
	ANNOUNCEMENTS(Known.Channel.ANNOUNCEMENTS),
	STAFF(Known.Channel.STAFF);

	override val ids = botChannel?.ids ?: listOf()
	override suspend fun fine(channelId: String, userId: String) = botChannel?.fine(channelId, userId) ?: true
}

suspend fun Channel.fine(botChannel: BotChannel, userId: String) = botChannel.fine(id, userId)
suspend fun ChannelClient.fine(botChannel: BotChannel, userId: String) = botChannel.fine(channelId, userId)