package com.ryosoftware.calls_blocker.service.callsblocker.logic

import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.allow.AllowExactNumberRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.allow.AllowPrefixNumberRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockAllRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockByCountryRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockExactNumberRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockGroupsOfContactsRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockNotContactsRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockNotDialedCallsRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockPrefixNumberRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockRejectedCallsRule
import com.ryosoftware.calls_blocker.service.callsblocker.logic.block.BlockRepeatedCallsRule
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow

@Module
@InstallIn(SingletonComponent::class)
abstract class RuleModule {

    @Binds
    @IntoSet
    abstract fun bindAllowExactNumberRule(
        rule: AllowExactNumberRule
    ): AbstractAllowRule

    @Binds
    @IntoSet
    abstract fun bindAllowPrefixNumberRule(
        rule: AllowPrefixNumberRule
    ): AbstractAllowRule

    @Binds
    @IntoSet
    abstract fun bindBlockExactNumberRule(
        rule: BlockExactNumberRule
    ): AbstractBlockRule

    @Binds
    @IntoSet
    abstract fun bindBlockPrefixNumberRule(
        rule: BlockPrefixNumberRule
    ): AbstractBlockRule

    @Binds
    @IntoSet
    abstract fun bindBlockAllRule(
        rule: BlockAllRule
    ): AbstractBlockRule

    @Binds
    @IntoSet
    abstract fun bindBlockNotContactsRule(
        rule: BlockNotContactsRule
    ): AbstractBlockRule

    @Binds
    @IntoSet
    abstract fun bindBlockGroupsNumberRule(
        rule: BlockGroupsOfContactsRule
    ): AbstractBlockRule

    @Binds
    @IntoSet
    abstract fun bindBlockByCountryRule(
        rule: BlockByCountryRule
    ): AbstractBlockRule

    @Binds
    @IntoSet
    abstract fun bindBlockNotDialledCallsRule(
        rule: BlockNotDialedCallsRule
    ): AbstractBlockRule

    @Binds
    @IntoSet
    abstract fun bindBlockRejectedCallsRule(
        rule: BlockRejectedCallsRule
    ): AbstractBlockRule

    @Binds
    @IntoSet
    abstract fun bindBlockRepeatedCallsRule(
        rule: BlockRepeatedCallsRule
    ): AbstractBlockRule
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun Collection<AbstractRule>.evaluateFirst(
    normalizedPhoneNumber: String,
    phoneNumber: String,
    normalizeToE164: (String?) -> String,
    isHiddenNumber: (String?) -> Boolean
): Reason =
    asFlow()
        .flatMapMerge { rule ->
            flow { emit(rule.evaluate(normalizedPhoneNumber, phoneNumber, normalizeToE164, isHiddenNumber)) }
        }
        .firstOrNull { it != Reason.NONE }
        ?: Reason.NONE

