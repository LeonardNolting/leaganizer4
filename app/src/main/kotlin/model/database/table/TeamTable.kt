package model.database.table

import org.jetbrains.exposed.dao.id.IntIdTable

object TeamTable : IntIdTable() {
	val name = text("name")
	val nick = text("nick").nullable()
	val tournament = reference("tournament", TournamentTable)
	val leader = reference("leader", ParticipantTable)
	val roleId = text("role_id")
	val textChannelId = text("text_channel_id")
	val voiceChannelId = text("voice_channel_id")
	val categoryId = text("category_id")
}