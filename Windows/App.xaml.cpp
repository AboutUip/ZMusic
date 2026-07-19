#include "pch.h"
#include "App.xaml.h"
#include "MainWindow.xaml.h"
#include "CacheStore.h"

#include <winrt/Windows.ApplicationModel.Core.h>

#include <thread>

using namespace winrt;
using namespace Microsoft::UI::Xaml;
using namespace winrt::Windows::ApplicationModel;
using namespace winrt::Windows::ApplicationModel::Core;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace winrt::ZMusic::implementation
{
    /// <summary>
    /// Initializes the singleton application object.  This is the first line of authored code
    /// executed, and as such is the logical equivalent of main() or WinMain().
    /// </summary>
    App::App()
    {
        CoreApplication::Suspending({ this, &App::OnCoreSuspending });

        // Xaml objects should not call InitializeComponent during construction.
        // See https://github.com/microsoft/cppwinrt/tree/master/nuget#initializecomponent

#if defined _DEBUG && !defined DISABLE_XAML_GENERATED_BREAK_ON_UNHANDLED_EXCEPTION
        UnhandledException([](IInspectable const&, UnhandledExceptionEventArgs const& e)
        {
            if (IsDebuggerPresent())
            {
                auto errorMessage = e.Message();
                __debugbreak();
            }
        });
#endif
    }

    void App::OnCoreSuspending(IInspectable const&, SuspendingEventArgs const& e)
    {
        SuspendingDeferral const deferral = e.SuspendingOperation().GetDeferral();
        [deferral]() -> winrt::fire_and_forget {
            co_await winrt::ZMusic::CacheStore::ClearAllAsync();
            deferral.Complete();
        }();
    }

    /// <summary>
    /// Invoked when the application is launched.
    /// </summary>
    /// <param name="e">Details about the launch request and process.</param>
    void App::OnLaunched([[maybe_unused]] LaunchActivatedEventArgs const& e)
    {
        // 先激活窗口，避免在清理临时缓存时长时间阻塞 UI 线程导致「点了启动没有任何反应」。
        window = make<MainWindow>();
        window.Activate();

        // 不得在 STA（UI 线程）上对 IAsync* 调用 .get()；在后台线程清理会话缓存。
        std::thread([] {
            winrt::init_apartment(apartment_type::multi_threaded);
            try
            {
                winrt::ZMusic::CacheStore::ClearAllAsync().get();
            }
            catch (...)
            {
            }
            winrt::uninit_apartment();
        }).detach();
    }
}
