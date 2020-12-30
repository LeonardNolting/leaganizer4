package command

import Config
import Config.Known.Role
import com.jessecorbett.diskord.api.model.GuildMember
import com.jessecorbett.diskord.api.model.User
import guild

interface Allowable {
	fun permitted(member: GuildMember): Boolean
}

class RoleCollection(private val roles: List<Allowable>, private val all: Boolean = false) : Allowable {
	override fun permitted(member: GuildMember): Boolean {
		return if (all) roles.all { it.permitted(member) } else roles.any { it.permitted(member) }
	}
}

object SuperUser : Allowable {
	override fun permitted(member: GuildMember): Boolean {
		return member.user!!.isSuperUser
	}
}

infix fun Allowable.or(other: Allowable) = RoleCollection(listOf(this, other))
infix fun Allowable.and(other: Allowable) = RoleCollection(listOf(this, other), all = true)

enum class Permission(private val allowable: Allowable? = null) : Allowable {
	EVERYBODY,
	VERIFIED(Role.VERIFIED),
	PARTICIPANT(VERIFIED and Role.PARTICIPANT),
	TRUSTED(VERIFIED and Role.TRUSTED),
	STAFF(VERIFIED and Role.STAFF),
	HOST(STAFF and Role.HOST),
	LEADER(PARTICIPANT and Role.LEADER),
	LEADER_OR_STAFF(LEADER or STAFF),
	SUPER_USER(SUPER_USER),
	REF(TRUSTED and Role.REF);

	override fun permitted(member: GuildMember) = allowable?.permitted(member) ?: true
}

suspend fun User.permitted(allowable: Allowable) = isSuperUser || allowable.permitted(guild.getMember(id))

val User.isSuperUser get() = Config.Known.User.values().any { user -> user.id == id && user.isSuperUser }
