package model.database.table

import model.database.zoneId
import model.type.ign
import model.type.nick
import model.type.skill
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.`java-time`.date
import org.jetbrains.exposed.sql.`java-time`.timestamp

object PlayerTable : IntIdTable() {
	val ign = ign("ign").uniqueIndex().nullable()
	val nick = nick("nick").nullable()
	val skill = skill("skill").nullable()
	val verified = bool("verified").default(false)
	val verifiedAt = timestamp("verified_at").nullable()
	val verifiedBy = reference("verified_by", PlayerTable, onDelete = ReferenceOption.SET_NULL).nullable()
	val minecraftId = text("minecraft_id")
	val hypixelId = text("hypixel_id")
	val discordId = text("discord_id")
	val appliedAt = timestamp("applied_at")
	val zoneId = zoneId("zone_id").nullable()
	val birthday = date("birthday").nullable()
	val trusted = bool("trusted").default(false)
	val ref = bool("ref").default(false)
	val valid = bool("valid").default(true)
}