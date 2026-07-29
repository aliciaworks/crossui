package dev.crossui.compiler

import dev.crossui.ir.DatePickerMode

internal fun winUiNativeLocalization(
    properties: List<WinLocalizedProperty>,
    localization: LocalizationRegistry,
): String = if (
    properties.any { localization.render(ExportTarget.WinUi3, it.text) == null }
) {
    """
    |    private Microsoft.Windows.ApplicationModel.Resources.ResourceManager? resourceManager;
    |    private Microsoft.Windows.ApplicationModel.Resources.ResourceContext? resourceContext;
    |    private Microsoft.Windows.ApplicationModel.Resources.ResourceMap? resourceMap;
    |    public Action<Exception>? LocalizationError { get; set; }
    |
    |    private void ConfigureLocalization(string? languageTag)
    |    {
    |        resourceManager ??= new();
    |        resourceContext = resourceManager.CreateResourceContext();
    |        if (!string.IsNullOrWhiteSpace(languageTag))
    |        {
    |            resourceContext.QualifierValues["Language"] = languageTag;
    |        }
    |        resourceMap ??= resourceManager.MainResourceMap.GetSubtree("Resources");
    |    }
    |
    |    private string Localize(string key, string fallback)
    |    {
    |        try
    |        {
    |            if (resourceContext is null)
    |            {
    |                ConfigureLocalization(null);
    |            }
    |            var value = resourceMap!
    |                .TryGetValue(key.Replace(".", "/"), resourceContext!)
    |                ?.ValueAsString;
    |            return string.IsNullOrEmpty(value) ? fallback : value;
    |        }
    |        catch (Exception exception)
    |        {
    |            LocalizationError?.Invoke(exception);
    |            return fallback;
    |        }
    |    }
    |
    |""".trimMargin()
} else {
    ""
}

internal fun csharpStateClass(
    stateName: String,
    properties: List<WinBindingProperty>,
): String {
    val members = properties.joinToString("\n\n", transform = ::stateMember)
    return """
        |public sealed class $stateName : INotifyPropertyChanged
        |{
        |    private readonly Action<string, string?> dispatch;
        |    private readonly DispatcherQueue dispatcherQueue;
        |
        |    public $stateName(
        |        Action<string, string?> dispatch,
        |        DispatcherQueue dispatcherQueue
        |    )
        |    {
        |        this.dispatch = dispatch;
        |        this.dispatcherQueue = dispatcherQueue;
        |    }
        |
        |    public event PropertyChangedEventHandler? PropertyChanged;
        |
        |$members
        |}
    """.trimMargin()
}

private fun stateMember(property: WinBindingProperty): String {
    if (property.temporalMode == DatePickerMode.DateTime) {
        return dateTimeStateMember(property)
    }
    val field = property.propertyName.replaceFirstChar(Char::lowercaseChar)
    val serialize = when (property.temporalMode) {
        DatePickerMode.Date ->
            "value?.ToString(\"yyyy-MM-dd\", CultureInfo.InvariantCulture)"
        DatePickerMode.Time ->
            "value?.ToString(\"hh\\\\:mm\\\\:ss\", CultureInfo.InvariantCulture)"
        DatePickerMode.DateTime ->
            "value?.ToUniversalTime().ToString(\"yyyy-MM-dd'T'HH:mm:ss'Z'\", CultureInfo.InvariantCulture)"
        null -> when (property.csharpType) {
            "bool" -> "value.ToString().ToLowerInvariant()"
            "double" -> "value.ToString(CultureInfo.InvariantCulture)"
            else -> "value"
        }
    }
    val dispatch = property.action?.let {
        """
        |        if (dispatchChange)
        |        {
        |            dispatch("${it.csharp()}", $serialize);
        |        }
        |""".trimMargin()
    }.orEmpty()
    return """
        |    private ${property.csharpType} $field = ${property.defaultValue};
        |
        |    public ${property.csharpType} ${property.propertyName}
        |    {
        |        get => $field;
        |        set => Set${property.propertyName}(value, true);
        |    }
        |
        |    public void Apply${property.propertyName}(${property.csharpType} value) =>
        |        Set${property.propertyName}(value, false);
        |
        |    private void Set${property.propertyName}(
        |        ${property.csharpType} value,
        |        bool dispatchChange
        |    )
        |    {
        |        if (!dispatcherQueue.HasThreadAccess)
        |        {
        |            _ = dispatcherQueue.TryEnqueue(
        |                () => Set${property.propertyName}(value, dispatchChange)
        |            );
        |            return;
        |        }
        |        if (Equals($field, value))
        |        {
        |            return;
        |        }
        |        $field = value;
        |        PropertyChanged?.Invoke(
        |            this,
        |            new PropertyChangedEventArgs(nameof(${property.propertyName}))
        |        );
        |$dispatch    }
    """.trimMargin()
}

private fun dateTimeStateMember(property: WinBindingProperty): String {
    val name = property.propertyName
    val field = name.replaceFirstChar(Char::lowercaseChar)
    val action = requireNotNull(property.action).csharp()
    return """
        |    private DateTimeOffset? $field = null;
        |
        |    public DateTimeOffset? ${name}Date
        |    {
        |        get => $field;
        |        set
        |        {
        |            if (value is null)
        |            {
        |                Set$name(null, true);
        |                return;
        |            }
        |            var time = $field?.TimeOfDay ?? TimeSpan.Zero;
        |            Set$name(
        |                new DateTimeOffset(value.Value.Date + time, value.Value.Offset),
        |                true
        |            );
        |        }
        |    }
        |
        |    public TimeSpan? ${name}Time
        |    {
        |        get => $field?.TimeOfDay;
        |        set
        |        {
        |            if (value is null)
        |            {
        |                Set$name(null, true);
        |                return;
        |            }
        |            var date = $field ?? DateTimeOffset.Now;
        |            Set$name(
        |                new DateTimeOffset(date.Date + value.Value, date.Offset),
        |                true
        |            );
        |        }
        |    }
        |
        |    public void Apply$name(DateTimeOffset? value) =>
        |        Set$name(value, false);
        |
        |    private void Set$name(DateTimeOffset? value, bool dispatchChange)
        |    {
        |        if (!dispatcherQueue.HasThreadAccess)
        |        {
        |            _ = dispatcherQueue.TryEnqueue(() => Set$name(value, dispatchChange));
        |            return;
        |        }
        |        if (Equals($field, value))
        |        {
        |            return;
        |        }
        |        $field = value;
        |        PropertyChanged?.Invoke(this, new(nameof(${name}Date)));
        |        PropertyChanged?.Invoke(this, new(nameof(${name}Time)));
        |        if (dispatchChange)
        |        {
        |            dispatch(
        |                "$action",
        |                value?.ToUniversalTime().ToString(
        |                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
        |                    CultureInfo.InvariantCulture
        |                )
        |            );
        |        }
        |    }
    """.trimMargin()
}
