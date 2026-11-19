package com.leekleak.trafficlight.integrations

import com.leekleak.iperf.di.iperfModule
import com.leekleak.trafficlight.iperf.iperfNavigationModule
import org.koin.dsl.bind
import org.koin.dsl.module

val integrationsModule = module {
    includes(iperfModule, iperfNavigationModule)
    single { ShizukuServicesProviderImpl(get(), get()) } bind ShizukuServicesProvider::class

    // Full is the only flavor with a root backend. Order here mirrors the product priority
    // Root -> Shizuku -> Basic; the coordinator also sorts by kind so the list cannot drift.
    single<PrivilegedAccessProvider> {
        PrivilegedAccessProviderImpl(
            backends = listOf(
                RootPrivilegedBackend(),
                ShizukuPrivilegedBackend(get<ShizukuServicesProviderImpl>()),
                BasicPrivilegedBackend(),
            ),
            appPreferenceRepo = get(),
            dataPlanManager = get(),
            scope = get(),
        )
    }
}
