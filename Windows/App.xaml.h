#pragma once

#include "App.xaml.g.h"

#include <winrt/Windows.ApplicationModel.h>

namespace winrt::ZMusic::implementation
{
    struct App : AppT<App>
    {
        App();

        void OnLaunched(Microsoft::UI::Xaml::LaunchActivatedEventArgs const&);

    private:
        void OnCoreSuspending(
            Windows::Foundation::IInspectable const&,
            Windows::ApplicationModel::SuspendingEventArgs const& e);

        winrt::Microsoft::UI::Xaml::Window window{ nullptr };
    };
}
