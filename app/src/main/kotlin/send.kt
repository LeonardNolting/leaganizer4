import com.jessecorbett.diskord.api.rest.CreateDM
import com.jessecorbett.diskord.api.rest.client.ChannelClient
import com.jessecorbett.diskord.dsl.CombinedMessageEmbed
import com.jessecorbett.diskord.util.sendMessage

/*operator fun MessageEdit.Companion.invoke(embed: CombinedMessageEmbed) =
	MessageEdit(embed.text, embed.embed())*/

suspend fun send(channel: ChannelClient, embed: CombinedMessageEmbed) =
	channel.sendMessage(embed.text, embed.embed())

suspend fun send(channelId: String, embed: CombinedMessageEmbed) =
	send(bot.clientStore.channels[channelId], embed)

suspend fun send(channel: Config.Known.Channel, embed: CombinedMessageEmbed) =
	send(channel.discordChannelClient, embed)


suspend fun dm(userId: String, embed: CombinedMessageEmbed) =
	send(bot.clientStore.discord.createDM(CreateDM(userId)).id, embed)

suspend fun send(channel: ChannelClient, block: suspend CombinedMessageEmbed.() -> Unit) =
	CombinedMessageEmbed().run {
		block()
		send(channel, this)
	}

suspend fun send(channelId: String, block: suspend CombinedMessageEmbed.() -> Unit) =
	send(bot.clientStore.channels[channelId], block)

suspend fun send(channel: Config.Known.Channel, block: suspend CombinedMessageEmbed.() -> Unit) =
	send(channel.discordChannelClient, block)

suspend fun dm(userId: String, block: suspend CombinedMessageEmbed.() -> Unit) =
	send(bot.clientStore.discord.createDM(CreateDM(userId)).id, block)