package com.log.simianv2.hook.base

import com.log.simianv2.dexkit.DexKitLookup

internal interface BaseHook {
    val TAG: String
        get() = javaClass.simpleName

    val name: String
        get() = TAG

    /** Resolve and retain every obfuscated member required by this feature. */
    fun initializeDexKit(lookup: DexKitLookup)

    /** Install interceptors after [initializeDexKit] succeeds. */
    fun install()
}