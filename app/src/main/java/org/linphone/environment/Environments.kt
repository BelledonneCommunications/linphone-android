package org.linphone.environment

import org.linphone.models.DimensionsEnvironment

val environments = listOf(
    DimensionsEnvironment(
        id = "NA",
        name = "North America",
        isDefault = false,
        isHidden = false,
        identityServerUri = "https://login.xarios.cloud",
        gatewayApiUri = "https://ucgateway.xarios.cloud",
        realtimeApiUri = "https://realtime.xarios.cloud",
        documentationUri = "https://docs.xarios.cloud",
        diagnosticsBlobConnectionString = "@~#DIAGBLOB_NA#~@",
        resourcesBlobUrl = "https://resource.xarios.cloud",
        locales = listOf("en-US")
    ),
    DimensionsEnvironment(
        id = "AU",
        name = "Australia",
        isDefault = false,
        isHidden = false,
        identityServerUri = "https://login.au.xarios.cloud",
        gatewayApiUri = "https://ucgateway.au.xarios.cloud",
        realtimeApiUri = "https://realtime.au.xarios.cloud",
        documentationUri = "https://docs.au.xarios.cloud",
        diagnosticsBlobConnectionString = "@~#DIAGBLOB_AU#~@",
        resourcesBlobUrl = "https://resource.au.xarios.cloud",
        locales = listOf("en-AU", "en-NZ")
    ),
    DimensionsEnvironment(
        id = "EU",
        name = "Europe",
        isDefault = false,
        isHidden = false,
        identityServerUri = "https://login.eu.xarios.cloud",
        gatewayApiUri = "https://ucgateway.eu.xarios.cloud",
        realtimeApiUri = "https://realtime.eu.xarios.cloud",
        documentationUri = "https://docs.eu.xarios.cloud",
        diagnosticsBlobConnectionString = "@~#DIAGBLOB_EU#~@",
        resourcesBlobUrl = "https://resource.eu.xarios.cloud",
        locales = listOf("en-IE")
    ),
    DimensionsEnvironment(
        id = "UK",
        name = "United Kingdom",
        isDefault = true,
        isHidden = false,
        identityServerUri = "https://login.uk.xarios.cloud",
        gatewayApiUri = "https://ucgateway.uk.xarios.cloud",
        realtimeApiUri = "https://realtime.uk.xarios.cloud",
        documentationUri = "https://docs.uk.xarios.cloud",
        diagnosticsBlobConnectionString = "@~#DIAGBLOB_UK#~@",
        resourcesBlobUrl = "https://resource.uk.xarios.cloud",
        locales = listOf("en-GB")
    ),
    DimensionsEnvironment(
        id = "Stg",
        name = "Staging",
        isDefault = false,
        isHidden = true,
        identityServerUri = "https://login.stage-env.dev",
        gatewayApiUri = "https://ucgateway.stage-env.dev",
        realtimeApiUri = "https://realtime.stage-env.dev",
        documentationUri = "https://docs.stage-env.dev",
        diagnosticsBlobConnectionString = "@~#DIAGBLOB_Stg#~@",
        resourcesBlobUrl = "https://resource.stage-env.dev",
        locales = emptyList()
    ),
    DimensionsEnvironment(
        id = "QA",
        name = "QA",
        isDefault = false,
        isHidden = true,
        identityServerUri = "https://login.xarios.dev",
        gatewayApiUri = "https://ucgateway.xarios.dev",
        realtimeApiUri = "https://realtime.xarios.dev",
        documentationUri = "https://docs.xarios.dev",
        diagnosticsBlobConnectionString = "@~#DIAGBLOB_QA#~@",
        resourcesBlobUrl = "https://resource.xarios.dev",
        locales = emptyList()
    ),
    DimensionsEnvironment(
        id = "RemoteDev",
        name = "Remote Development",
        isDefault = false,
        isHidden = true,
        identityServerUri = "https://grapefruit-idp.cowling.dev",
        gatewayApiUri = "https://grapefruit-ucgateway.cowling.dev",
        realtimeApiUri = "https://grapefruit-realtime.cowling.dev",
        documentationUri = "https://docs.xarios.dev",
        diagnosticsBlobConnectionString = "@~#DIAGBLOB_RemoteDev#~@",
        resourcesBlobUrl = "https://resource.xarios.dev",
        locales = emptyList()
    )
)
