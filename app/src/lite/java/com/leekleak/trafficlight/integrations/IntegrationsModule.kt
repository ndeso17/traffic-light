package com.leekleak.trafficlight.integrations

import org.koin.dsl.bind
import org.koin.dsl.module

val integrationsModule = module {
    single { ShizukuServicesProviderImpl(get()) } bind ShizukuServicesProvider::class

    // Lite has no privileged backend at all: the coordinator always resolves to Basic.
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
