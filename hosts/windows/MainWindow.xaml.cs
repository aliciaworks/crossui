using CrossUi.Generated;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;

namespace CrossUi.Windows;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        SystemBackdrop = new MicaBackdrop();
        Root.Children.Add(new CrossUiShowcase());
    }
}
