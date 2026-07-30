using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Shapes;
using ZMusic.Distribution;
using ZMusic.Setup.Pages;
using IoPath = System.IO.Path;

namespace ZMusic.Setup;

public partial class MainWindow : Window
{
    private readonly WizardState _state = new();
    private WizardPage _page = WizardPage.Welcome;
    private CancellationTokenSource? _installCts;
    private readonly Ellipse[] _stepDots;

    public MainWindow()
    {
        InitializeComponent();
        _stepDots = [Step1, Step2, Step3, Step4, Step5, Step6];
        Loaded += (_, _) => StartAmbientMotion();
        ShowPage(WizardPage.Welcome);
    }

    private void StartAmbientMotion()
    {
        AnimateGlow(GlowA, 0.12, 0.24, TimeSpan.FromSeconds(6.5));
        AnimateGlow(GlowB, 0.06, 0.16, TimeSpan.FromSeconds(8.2));
    }

    private static void AnimateGlow(UIElement target, double from, double to, TimeSpan duration)
    {
        var anim = new DoubleAnimation(from, to, duration)
        {
            AutoReverse = true,
            RepeatBehavior = RepeatBehavior.Forever,
            EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut },
        };
        target.BeginAnimation(OpacityProperty, anim);
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton == MouseButton.Left)
        {
            DragMove();
        }
    }

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    private void ShowPage(WizardPage page)
    {
        _page = page;
        FrameworkElement content = page switch
        {
            WizardPage.Welcome => new WelcomePage(),
            WizardPage.License => new LicensePage(_state),
            WizardPage.Options => new OptionsPage(_state),
            WizardPage.Location => new LocationPage(_state),
            WizardPage.Progress => new ProgressPage(),
            WizardPage.Finished => new FinishedPage(_state),
            _ => new WelcomePage(),
        };

        PageHost.Content = content;
        PageMotion.PlayEnter(content);
        UpdateStepChrome(page);

        BackButton.Visibility = page is WizardPage.Welcome or WizardPage.Progress or WizardPage.Finished
            ? Visibility.Collapsed
            : Visibility.Visible;

        NextButton.Visibility = page == WizardPage.Progress ? Visibility.Collapsed : Visibility.Visible;
        NextButton.Content = page switch
        {
            WizardPage.Welcome => "开始",
            WizardPage.Location => "安装",
            WizardPage.Finished => _state.InstallSucceeded ? "完成" : "关闭",
            _ => "下一步",
        };

        if (page == WizardPage.License)
        {
            SyncLicenseNextEnabled();
        }
        else
        {
            NextButton.IsEnabled = true;
        }
    }

    private void UpdateStepChrome(WizardPage page)
    {
        var index = page switch
        {
            WizardPage.Welcome => 0,
            WizardPage.License => 1,
            WizardPage.Options => 2,
            WizardPage.Location => 3,
            WizardPage.Progress => 4,
            WizardPage.Finished => 5,
            _ => 0,
        };

        StepLabel.Text = page switch
        {
            WizardPage.Welcome => "首页",
            WizardPage.License => "协议",
            WizardPage.Options => "配置",
            WizardPage.Location => "位置",
            WizardPage.Progress => "安装",
            WizardPage.Finished => "完成",
            _ => string.Empty,
        };

        var white = (Brush)FindResource("BrushWhite");
        var soft = (Brush)FindResource("BrushSoft");
        var line = (Brush)FindResource("BrushLine");

        for (var i = 0; i < _stepDots.Length; i++)
        {
            if (i < index)
            {
                _stepDots[i].Fill = soft;
                _stepDots[i].Opacity = 0.55;
            }
            else if (i == index)
            {
                _stepDots[i].Fill = white;
                _stepDots[i].Opacity = 1;
                PulseDot(_stepDots[i]);
            }
            else
            {
                _stepDots[i].Fill = line;
                _stepDots[i].Opacity = 1;
            }
        }
    }

    private static void PulseDot(Ellipse dot)
    {
        var scale = new ScaleTransform(1, 1);
        dot.RenderTransform = scale;
        dot.RenderTransformOrigin = new Point(0.5, 0.5);
        var anim = new DoubleAnimation(1, 1.35, TimeSpan.FromMilliseconds(520))
        {
            AutoReverse = true,
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut },
        };
        scale.BeginAnimation(ScaleTransform.ScaleXProperty, anim);
        scale.BeginAnimation(ScaleTransform.ScaleYProperty, anim);
    }

    internal void SyncLicenseNextEnabled()
    {
        if (_page == WizardPage.License)
        {
            NextButton.IsEnabled = _state.LicenseAccepted;
        }
    }

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        ShowPage(_page switch
        {
            WizardPage.License => WizardPage.Welcome,
            WizardPage.Options => WizardPage.License,
            WizardPage.Location => WizardPage.Options,
            _ => _page,
        });
    }

    private async void Next_Click(object sender, RoutedEventArgs e)
    {
        switch (_page)
        {
            case WizardPage.Welcome:
                ShowPage(WizardPage.License);
                break;
            case WizardPage.License:
                if (!_state.LicenseAccepted)
                {
                    return;
                }

                ShowPage(WizardPage.Options);
                break;
            case WizardPage.Options:
                ShowPage(WizardPage.Location);
                break;
            case WizardPage.Location:
                if (PageHost.Content is LocationPage locationPage)
                {
                    locationPage.Commit();
                }

                if (!ValidateInstallDirectory(out var error))
                {
                    MessageBox.Show(this, error, "安装位置", MessageBoxButton.OK, MessageBoxImage.None);
                    return;
                }

                ShowPage(WizardPage.Progress);
                await RunInstallAsync();
                break;
            case WizardPage.Finished:
                if (_state.InstallSucceeded && _state.LaunchWhenFinished)
                {
                    TryLaunchApp();
                }

                Close();
                break;
        }
    }

    private bool ValidateInstallDirectory(out string error)
    {
        error = string.Empty;
        var path = _state.InstallDirectory.Trim();
        if (string.IsNullOrWhiteSpace(path))
        {
            error = "请填写安装目录。";
            return false;
        }

        try
        {
            path = IoPath.GetFullPath(path);
            _state.InstallDirectory = path;
        }
        catch
        {
            error = "安装路径无效。";
            return false;
        }

        if (_state.Scope == InstallScope.PerUser && IsProtectedMachinePath(path))
        {
            error = "当前用户安装不能写入 Program Files。请改路径，或改选「整机安装」。";
            return false;
        }

        try
        {
            Directory.CreateDirectory(path);
            var probe = IoPath.Combine(path, ".zmusic-write-test");
            File.WriteAllText(probe, "ok");
            File.Delete(probe);
        }
        catch
        {
            error = _state.Scope == InstallScope.PerMachine
                ? "无法写入该目录。整机安装将在下一步请求管理员权限；也可换一个可写路径。"
                : "无法写入该目录，请更换安装位置。";
            if (_state.Scope == InstallScope.PerMachine)
            {
                return true;
            }

            return false;
        }

        return true;
    }

    private static bool IsProtectedMachinePath(string path)
    {
        try
        {
            var full = IoPath.GetFullPath(path);
            var pf = IoPath.GetFullPath(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles));
            var pf86 = IoPath.GetFullPath(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86));
            return full.StartsWith(pf, StringComparison.OrdinalIgnoreCase)
                   || full.StartsWith(pf86, StringComparison.OrdinalIgnoreCase);
        }
        catch
        {
            return false;
        }
    }

    private async Task RunInstallAsync()
    {
        var progressPage = PageHost.Content as ProgressPage;
        progressPage?.SetStatus("正在准备安装包…");

        string? tempDir = null;
        try
        {
            _installCts = new CancellationTokenSource();
            tempDir = InstallInventory.CreateSetupTempDirectory();
            var msiPath = IoPath.Combine(tempDir, "ZMusic.msi");
            EmbeddedResources.ExtractToFile("ZMusic.Setup.ZMusic.msi", msiPath);

            progressPage?.SetStatus(
                _state.Scope == InstallScope.PerMachine
                    ? "正在安装（可能弹出 UAC）…"
                    : "正在静默安装…");
            progressPage?.SetIndeterminate(true);

            var request = new SilentInstallRequest(
                msiPath,
                _state.InstallDirectory,
                _state.Scope,
                _state.DesktopShortcut,
                _state.StartMenuShortcut);

            var result = await MsiExec.InstallSilentAsync(request, _installCts.Token);
            _state.LastExitCode = result.ExitCode;
            _state.InstallSucceeded = result.ExitCode is 0 or 3010;
            _state.MsiLogPath = result.LogPath;

            if (_state.InstallSucceeded)
            {
                _state.StatusMessage = "安装完成。";
                if (!string.IsNullOrWhiteSpace(result.LogPath))
                {
                    try
                    {
                        File.Delete(result.LogPath);
                    }
                    catch
                    {
                        // ignore
                    }
                }
            }
            else if (!string.IsNullOrWhiteSpace(result.LogPath))
            {
                _state.StatusMessage =
                    $"安装失败（退出码 {result.ExitCode}）。详细日志：{result.LogPath}";
            }
            else
            {
                _state.StatusMessage = $"安装失败（退出码 {result.ExitCode}）。";
            }
        }
        catch (Exception ex)
        {
            _state.InstallSucceeded = false;
            _state.LastExitCode = -1;
            _state.StatusMessage = $"安装中断：{ex.GetType().Name}";
        }
        finally
        {
            InstallInventory.TryDeleteDirectory(tempDir);
            InstallInventory.CleanupOrphanedSetupTempDirectories();
            progressPage?.SetIndeterminate(false);
            ShowPage(WizardPage.Finished);
        }
    }

    private void TryLaunchApp()
    {
        try
        {
            var exe = IoPath.Combine(_state.InstallDirectory, ProductIdentity.MainExecutableName);
            if (File.Exists(exe))
            {
                Process.Start(new ProcessStartInfo(exe) { UseShellExecute = true });
            }
        }
        catch
        {
            // ignore launch failures
        }
    }
}
