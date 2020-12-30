package model.type

import model.database.IntegerWrapperColumnType
import model.database.WrapperColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

// https://youtrack.jetbrains.com/issue/KT-34024
data class Skill(override val value: Int) : WrapperColumnType.Wrapper

class SkillColumnType : IntegerWrapperColumnType<Skill>(Skill::class)

fun Table.skill(name: String): Column<Skill> = registerColumn(name, SkillColumnType())