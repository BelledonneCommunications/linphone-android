# SIP X-Headers Support for DesktopPBX Integration

## Overview

This implementation adds comprehensive SIP X-Headers support to the linphone-android application, enabling integration with DesktopPBX systems for advanced call routing, PSTN dialing, and SIP-to-SIP communication.

## Components

### 1. **SipHeadersUtils** (`app/src/main/java/org/linphone/utils/SipHeadersUtils.kt`)

Core utility class for managing SIP X-Headers in call parameters.

#### Key Features:
- Add individual custom headers to CallParams
- Add multiple headers in batch
- Predefined DesktopPBX headers and routing types
- Session ID generation for call tracking
- Support for SIP-to-SIP and PSTN routing headers

#### Usage Examples:

```kotlin
// Add a custom header
SipHeadersUtils.addCustomHeader(params, "X-Custom-Header", "value")

// Add DesktopPBX SIP-to-SIP headers
SipHeadersUtils.addDesktopPBXSipToSipHeaders(
    params,
    sourceDevice = "mobile-android",
    sessionId = SipHeadersUtils.generateSessionId()
)

// Add DesktopPBX PSTN headers
SipHeadersUtils.addDesktopPBXPstnHeaders(
    params,
    pstnGateway = "pbx.example.com",
    routeType = SipHeadersUtils.CallRoutingType.SIP_TO_PSTN,
    sessionId = SipHeadersUtils.generateSessionId()
)
```

#### Predefined Headers:
- `X-Linphone-Features`: Capabilities advertisement
- `X-Source-Device`: Device identifier
- `X-Call-Routing`: Routing type (sip-to-sip, sip-to-pstn, etc.)
- `X-Session-ID`: Unique session tracking ID
- `X-Priority`: Call priority level
- `X-PSTN-Gateway`: PSTN gateway address
- `X-SIP-Proxy`: SIP proxy information

### `X-Dial-Number` (Dialed Number Header)

- `X-Dial-Number`: The raw phone number dialed by the user for PSTN calls. This header is added only for PSTN routing (when `X-Call-Routing: sip-to-pstn` is applied) and contains the canonical E.164 or digit string that will be sent to the PSTN gateway.

Example headers for a PSTN call:

```
X-Call-Routing: sip-to-pstn
X-PSTN-Gateway: pbx.example.com
X-Dial-Number: +1234567890
X-Session-ID: [tracking-id]
```

Notes:
- Use `X-Dial-Number` for PBX integrations that require the original dialed number separately from the SIP Request-URI.
- Do not place sensitive data in headers; treat header values as potentially visible in transit unless using SIPS.
 - For legacy or partner systems that expect the older header name, the client also sends `X-HDEAR` with the same dialed number.

### 2. **SipHeadersPreferences** (`app/src/main/java/org/linphone/core/SipHeadersPreferences.kt`)

Configuration management class for X-Headers settings.

#### Configuration Options:
- `xHeadersEnabled`: Enable/disable X-Headers globally
- `desktopPBXMode`: Enable DesktopPBX integration mode
- `pstnGatewayAddress`: PSTN gateway server address
- `sourceDeviceId`: Device identifier for headers
- `enableSessionTracking`: Enable unique session ID generation
- `autoAddHeaders`: Automatically add headers to all calls
- `customHeaders`: User-defined custom headers

#### Storage Format:
Custom headers are stored as plain text with one header per line:
```
X-Custom-Header-1: value1
X-Custom-Header-2: value2
X-Another-Header: value3
```

### 3. **CoreContext Integration**

Enhanced the CoreContext's `startCall()` method to automatically apply X-Headers:

```kotlin
@WorkerThread
private fun applyCustomSipHeaders(params: CallParams, address: Address, username: String)
```

Features:
- Automatic detection of PSTN vs SIP calls
- Conditional header application based on call type
- Session ID generation and tracking
- Custom header injection
- Error handling and logging

### 4. **SipHeadersSettingsViewModel** (`app/src/main/java/org/linphone/ui/settings/viewmodel/SipHeadersSettingsViewModel.kt`)

ViewModel for managing X-Headers settings UI.

#### Methods:
- `toggleXHeadersEnabled()`: Enable/disable X-Headers
- `toggleDesktopPBXMode()`: Toggle DesktopPBX mode
- `updatePstnGateway(gateway: String)`: Set PSTN gateway
- `updateSourceDeviceId(deviceId: String)`: Set device identifier
- `toggleSessionTracking()`: Enable/disable session tracking
- `toggleAutoAddHeaders()`: Toggle auto header addition
- `updateCustomHeaders(headers: String)`: Update custom headers
- `validateAndSaveCustomHeaders()`: Validate header format

### 5. **SipHeadersModel** (`app/src/main/java/org/linphone/ui/settings/model/SipHeadersModel.kt`)

UI data model for X-Headers settings display.

## Call Routing Support

### SIP-to-SIP Routing
When calling another SIP address:
```
Headers Applied:
- X-Call-Routing: sip-to-sip
- X-Source-Device: [device-id]
- X-Session-ID: [tracking-id]
- X-Linphone-Features: sip-to-sip,e2e-encryption,video-call,audio-conference
```

### PSTN Dialing (Phone Numbers)
When calling a phone number (detected by + prefix or all digits):
```
Headers Applied:
- X-Call-Routing: sip-to-pstn
- X-PSTN-Gateway: [gateway-address]
- X-Session-ID: [tracking-id]
- X-Linphone-Features: pstn-dialing,ivr-support,call-recording,transfer
```

## Configuration Examples

### Example 1: Enable DesktopPBX with PSTN Gateway

```kotlin
coreContext.postOnCoreThread { core ->
    val sipHeaders = corePreferences.sipHeaders()
    sipHeaders.xHeadersEnabled = true
    sipHeaders.desktopPBXMode = true
    sipHeaders.pstnGatewayAddress = "pbx.example.com"
    sipHeaders.enableSessionTracking = true
    sipHeaders.sourceDeviceId = "mobile-android-office"
}
```

### Example 2: Custom Headers for Specific Use Case

```kotlin
val customHeaders = listOf(
    "X-Department" to "Sales",
    "X-Cost-Center" to "CC-12345",
    "X-Custom-Router" to "yes"
)
corePreferences.sipHeaders().storeCustomHeaders(customHeaders)
```

### Example 3: Disable Headers for Specific Calls

Headers can be disabled globally via settings or by clearing the configuration:
```kotlin
corePreferences.sipHeaders().xHeadersEnabled = false
```

## Call Handling Logic

### Automatic Call Type Detection

The implementation automatically detects call types:

1. **PSTN Call** (Phone Number):
   - Username starts with + (e.g., +1234567890)
   - Username is all digits (e.g., 1234567890)
   - Username contains only digits, hyphens, and spaces

2. **SIP Call**:
   - Any other format (e.g., user@example.com, alice@pbx.local)

### Header Application Flow

```
startCall(address, params)
    ↓
applyCustomSipHeaders(params, address, username)
    ↓
Check if X-Headers enabled → if not, return
    ↓
Detect call type (PSTN vs SIP)
    ↓
Apply DesktopPBX headers (if mode enabled)
    ├─ PSTN Call: addDesktopPBXPstnHeaders()
    └─ SIP Call: addDesktopPBXSipToSipHeaders()
    ↓
Add user custom headers (if auto-add enabled)
    ↓
inviteAddressWithParams(address, params)
```

## String Resources Required

The following string resources should be added to `res/values/strings.xml`:

```xml
<!-- SIP Headers Settings -->
<string name="sip_headers_enable">Enable X-Headers</string>
<string name="sip_headers_enable_description">Enable custom SIP X-Headers for all calls</string>
<string name="desktop_pbx_mode">DesktopPBX Mode</string>
<string name="desktop_pbx_mode_description">Enable DesktopPBX integration with automatic header injection</string>
<string name="pstn_gateway_address">PSTN Gateway Address</string>
<string name="pstn_gateway_address_description">Server address for PSTN gateway routing</string>
<string name="source_device_id">Source Device ID</string>
<string name="source_device_id_description">Identifier for this device in outgoing calls</string>
<string name="enable_session_tracking">Enable Session Tracking</string>
<string name="enable_session_tracking_description">Generate unique session IDs for call tracking</string>
<string name="auto_add_headers">Auto-Add Headers</string>
<string name="auto_add_headers_description">Automatically add custom headers to all calls</string>
<string name="custom_sip_headers">Custom SIP Headers</string>
<string name="custom_sip_headers_description">One header per line (format: Header-Name: value)</string>
```

## Logging

All operations are logged with "SIP Headers" prefix for easy debugging:

```
[SIP Headers Utils] Adding custom header [X-Custom: value]
[SIP Headers Preferences] Setting X-Headers enabled: true
[SIP Headers Settings ViewModel] Settings loaded successfully
[Core Context] Detected PSTN call, applying PSTN headers
```

## Error Handling

- Invalid header formats are logged and skipped
- Missing CallParams are handled gracefully
- Exception during header addition is caught and logged
- Custom header validation prevents invalid entries

## Integration Points

The X-Headers system integrates at these key points:

1. **Call Initiation**: `CoreContext.startCall()`
2. **Account Configuration**: Via `CorePreferences` settings
3. **User Interface**: Through `SipHeadersSettingsViewModel`
4. **SIP Protocol**: Applied to `CallParams` before invite

## Performance Considerations

- Header validation is performed only during explicit updates
- Session ID generation uses lightweight timestamp + random approach
- String parsing is only done when needed for custom headers
- Core thread dispatching prevents UI blocking

## Security Notes

- No sensitive information should be placed in X-Headers
- Headers are transmitted in SIP messages (not encrypted unless using SIPS)
- Header values are user-configurable and stored in local configuration
- No sensitive data from headers is logged in production

## Testing

Key test scenarios:

1. Enable/disable X-Headers functionality
2. SIP-to-SIP call with headers
3. PSTN call with headers
4. Custom header parsing and validation
5. Session tracking across multiple calls
6. DesktopPBX mode with gateway configuration

## Future Enhancements

Potential additions:

1. Header templates for different PBX systems
2. Dynamic header values based on call context
3. Header logging and analytics
4. Template variables (e.g., `{timestamp}`, `{callid}`)
5. Conditional header application rules
6. Header history and audit logging

## References

- [RFC 3261 - SIP Protocol](https://tools.ietf.org/html/rfc3261)
- [Linphone SDK Documentation](https://www.linphone.org/technical-help/liblinphone)
- [SIP X-Headers Extensions](https://tools.ietf.org/html/draft-ietf-sip-extensions)
