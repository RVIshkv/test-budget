package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.authorId = body.authorId
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val query = BudgetTable
                .select { BudgetTable.year eq param.year }

            val data = queryToData(query, param.name)
            val total = query.count()

            val queryWithLimit = query.limit(param.limit, param.offset)
                .orderBy(BudgetTable.month to SortOrder.ASC)
                .orderBy(BudgetTable.amount to SortOrder.DESC)

            val dataWithLimit = queryToData(queryWithLimit, param.name)

            val sumByType = data.groupBy { it.type.name }.mapValues { it.value.sumOf { v -> v.amount } }

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = dataWithLimit
            )
        }
    }

    private fun queryToData(query: Query, name: String?): List<BudgetRecordWithAuthorDate> {
        val budgetRecordWithAuthorDateList = BudgetEntity.wrapRows(query)
            .map { it.toResponse() }
            .map { budgetRecord ->
                if (budgetRecord.authorId != null) {
                    val authorQuery = AuthorTable.select { AuthorTable.id eq budgetRecord.authorId }
                    val authorEntity = AuthorEntity.wrapRow(authorQuery.first())

                    val fullName = authorEntity.fullName

                    val createdDateTime = authorEntity.createdDateTime
                    val pattern = DateTimeFormat.forPattern("HH:mm:ss dd.MM.yyyy")
                    val createdDateTimeString = pattern.print(createdDateTime)


                    BudgetRecordWithAuthorDate(budgetRecord, fullName, createdDateTimeString)
                } else {
                    BudgetRecordWithAuthorDate(budgetRecord)
                }
            }

        return if (name != null && name.isNotBlank()) {
            budgetRecordWithAuthorDateList.filter { b -> b.authorFullName != null && b.authorFullName.contains(name, true)}
        } else {
            budgetRecordWithAuthorDateList
        }
    }
}