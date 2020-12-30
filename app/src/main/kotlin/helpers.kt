import com.jessecorbett.diskord.api.model.Overwrite
import com.jessecorbett.diskord.api.model.OverwriteType
import com.jessecorbett.diskord.api.model.Permissions
import com.jessecorbett.diskord.api.rest.client.ChannelClient
import command.Channels
import command.Command
import command.Permission
import command.transactionSuspend
import model.Player
import model.Tournament
import model.database.table.ParticipantTable
import model.database.table.PlayerTable
import model.display
import model.refreshNick
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZonedDateTime

@Command(
	"helper", "refreshMinecraftIds",
	permission = Permission.SUPER_USER,
	description = "Refreshes all minecraft ids.",
	onSuccess = "Refreshing done.",
	channels = Channels.STAFF
)
fun refreshMinecraftIds() = transactionSuspend {
	Player.allValid().forEach {
		val ign = it.ign
		if (ign != null) it.minecraftId = Config.Api.Mojang.IGN(ign).call().id
	}
}

@Command(
	"helper", "refreshAnnouncement",
	permission = Permission.SUPER_USER,
	channels = Channels.STAFF,
	description = "Refresh the current tournaments announcement.",
	onSuccess = "Refreshed announcement."
)
fun refreshAnnouncement() = Tournament.current.refreshAnnouncement(reason = "Debugging.")

@Command(
	"helper", "parseDates",
	permission = Permission.SUPER_USER,
	channels = Channels.STAFF,
	description = "Parse some dates."
)
fun parseDates(
	starts: List<ZonedDateTime>,
) = starts.map { it.toInstant() }

@Command(
	"helper", "refreshIGNs",
	permission = Permission.SUPER_USER,
	channels = Channels.STAFF,
	description = "Refresh IGNs and set their nicks accordingly."
)
fun refreshIGNs() = transactionSuspend {
	Player.allValid().forEach { player -> player.refreshIGN(false) }
	Tournament.current.refreshAnnouncement(reason = "Updated IGNs.")
}

@Command(
	"helper", "refreshNicks",
	permission = Permission.SUPER_USER,
	channels = Channels.STAFF,
	description = "Refresh everybody's nicks.",
	warning = "Only updates 500 at a time.",
	onSuccess = "Refreshed nicks."
)
fun refreshNicks() = transactionSuspend {
	val players = Player.allValid().toList()
	guild.getMembers(500).forEach { member ->
		val user = member.user!!
		val player = players.find { it.discordId == user.id }
		if (player == null) member.user!!.refreshNick(false)
		else player.refreshNick(false)
	}
}

@Command(
	"helper", "makeParticipate",
	permission = Permission.SUPER_USER,
	description = "Make the player participate.",
	onSuccess = "They're now participating."
)
fun makeParticipate(
	player: Player,
	@Command.Meta
	channel: ChannelClient
) = Tournament.participate(player, channel, listOf())

@Command(
	"helper", "generateParticipants",
	permission = Permission.SUPER_USER,
	description = "Make n players participate in the tournament."
)
fun generateParticipants(
	n: Int
) = transaction {
	val tournament = Tournament.current
	val players = Player.find {
		(PlayerTable.valid eq true) and
			(PlayerTable.verified eq true) and (
			PlayerTable.id notInSubQuery ParticipantTable
				.slice(ParticipantTable.player)
				.select {
					(ParticipantTable.player eq PlayerTable.id) and (ParticipantTable.tournament eq tournament.id) and (ParticipantTable.quit eq null)
				}
			)
	}.toMutableList()
	require(players.size >= n) {
		"There are only ${players.size} players who are not already participating but could. No participants were added. Available players: " +
			players.display()
	}

	val addedPlayers = mutableListOf<Player>()
	repeat(n) {
		val player = players.random()
		players -= player
		addedPlayers += player
		makeParticipate(player, Config.Known.Channel.STAFF.discordChannelClient)
	}
	"Added the following players: " + addedPlayers.display()
}

@Command(
	"helper", "getRoles",
	permission = Permission.SUPER_USER,
)
suspend fun getRoles() = guild.get().roles.map { it.name }

@Command(
	"helper", "deleteChannelsAndRoles",
	permission = Permission.SUPER_USER,
	onSuccess = "Deleted channels and roles."
)
suspend fun deleteChannelsAndRole() = Tournament.current.deleteChannels()

@Command(
	"helper", "refreshChannelPermissions",
	permission = Permission.SUPER_USER,
	onSuccess = "Refreshed permission.",
)
fun refreshChannelPermissions() = transactionSuspend {
//	val tournament = Tournament.current
	val category = bot.clientStore.channels["793491975592476672"].editPermissions(
		Overwrite(
			Config.Known.Role.EVERYONE.id, OverwriteType.ROLE, allowed = Permissions.NONE, denied = Permissions.ALL
		)
	)
}