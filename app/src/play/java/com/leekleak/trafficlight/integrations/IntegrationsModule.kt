package com.leekleak.trafficlight.integrations

import com.leekleak.iperf.di.iperfModule
import com.leekleak.play_integration.playModule
import com.leekleak.trafficlight.iperf.iperfNavigationModule
import org.koin.dsl.bind
import org.koin.dsl.module

val integrationsModule = module {
    includes(playModule, iperfModule, iperfNavigationModule)
    single { PlayServicesProviderImpl(get()) } bind PlayServicesProvider::class
    single { ShizukuServicesProviderImpl(get()) } bind ShizukuServicesProvider::class

    // Play ships no privileged backend: Shizuku is a stub and the coordinator lands in Basic.
    single<PrivilegedAccessProvider> {
        PrivilegedAccessProviderImpl(
            backends = listOf(
                ShizukuPrivilegedBackend(),
                BasicPrivilegedBackend(),
            ),
            appPreferenceRepo = get(),
            dataPlanManager = get(),
            scope = get(),
        )
    }
}
