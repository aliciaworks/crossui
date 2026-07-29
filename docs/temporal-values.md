# Temporal values

CrossUI date and time controls use strings at the native action boundary, with
one deterministic format per semantic mode:

- `Date`: `yyyy-MM-dd`
- `Time`: `HH:mm:ss`
- `DateTime`: UTC ISO-8601, `yyyy-MM-dd'T'HH:mm:ss'Z'`

Typed documents require a state binding:

```kotlin
datePicker(
    key = "appointment",
    value = bind(ScreenState::appointment),
    mode = DatePickerMode.DateTime,
    onChange = "appointment_changed",
)
```

The state property is `String?`. Parse it into the project's preferred
`kotlinx-datetime`, Java time, or domain type in the action mapper or reducer.
The semantic IR does not serialize platform `Date`, epoch milliseconds, or
`DateTimeOffset`, so the same action payload has the same meaning everywhere.

SwiftUI generates a real `Binding<Date>`. Android generates Material 3 date
and time controls. WinUI DateTime mode generates a `CalendarDatePicker` and a
`TimePicker` backed by one generated state property.
