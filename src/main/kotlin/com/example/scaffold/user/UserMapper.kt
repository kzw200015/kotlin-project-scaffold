package com.example.scaffold.user

import com.example.scaffold.user.UserDynamicSqlSupport.createdAt
import com.example.scaffold.user.UserDynamicSqlSupport.email
import com.example.scaffold.user.UserDynamicSqlSupport.id
import com.example.scaffold.user.UserDynamicSqlSupport.name
import com.example.scaffold.user.UserDynamicSqlSupport.user
import org.apache.ibatis.annotations.InsertProvider
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Options
import org.apache.ibatis.annotations.SelectProvider
import org.mybatis.dynamic.sql.insert.render.InsertStatementProvider
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider
import org.mybatis.dynamic.sql.util.SqlProviderAdapter
import org.mybatis.dynamic.sql.util.kotlin.CountCompleter
import org.mybatis.dynamic.sql.util.kotlin.DeleteCompleter
import org.mybatis.dynamic.sql.util.kotlin.SelectCompleter
import org.mybatis.dynamic.sql.util.kotlin.UpdateCompleter
import org.mybatis.dynamic.sql.util.kotlin.elements.isEqualTo
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.countFrom
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.deleteFrom
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.insert
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.selectList
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.selectOne
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.update
import org.mybatis.dynamic.sql.util.mybatis3.CommonCountMapper
import org.mybatis.dynamic.sql.util.mybatis3.CommonDeleteMapper
import org.mybatis.dynamic.sql.util.mybatis3.CommonUpdateMapper

@Mapper
interface UserMapper : CommonCountMapper, CommonDeleteMapper, CommonUpdateMapper {

	@InsertProvider(type = SqlProviderAdapter::class, method = "insert")
	@Options(useGeneratedKeys = true, keyProperty = "row.id,row.createdAt", keyColumn = "id,created_at")
	fun insert(insertStatement: InsertStatementProvider<UserRecord>): Int

	@SelectProvider(type = SqlProviderAdapter::class, method = "select")
	fun selectOne(selectStatement: SelectStatementProvider): UserRecord?

	@SelectProvider(type = SqlProviderAdapter::class, method = "select")
	fun selectMany(selectStatement: SelectStatementProvider): List<UserRecord>
}

private val columnList = listOf(id, name, email, createdAt)

fun UserMapper.insert(row: UserRecord): Int =
	insert(this::insert, row, user) {
		map(name) toProperty "name"
		map(email) toProperty "email"
	}

fun UserMapper.selectOne(completer: SelectCompleter): UserRecord? =
	selectOne(this::selectOne, columnList, user, completer)

fun UserMapper.select(completer: SelectCompleter): List<UserRecord> =
	selectList(this::selectMany, columnList, user, completer)

fun UserMapper.selectById(id: Long): UserRecord? =
	selectOne { where { UserDynamicSqlSupport.id isEqualTo id } }

fun UserMapper.count(completer: CountCompleter): Long =
	countFrom(this::count, user, completer)

fun UserMapper.update(completer: UpdateCompleter): Int =
	update(this::update, user, completer)

fun UserMapper.delete(completer: DeleteCompleter): Int =
	deleteFrom(this::delete, user, completer)
