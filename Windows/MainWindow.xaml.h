#pragma once

#include "MainWindow.g.h"
#include "AppSettings.h"
#include "LrcParser.h"
#include "SessionStore.h"
#include "NcmLibrary.h"

#include <random>

#include <winrt/Windows.Media.Playback.h>

namespace winrt::ZMusic::implementation
{
    /** 与 Android PlaybackMode 一致：顺序 / 单曲循环 / 随机。 */
    enum class LibraryPlaybackMode : uint8_t
    {
        Order = 0,
        RepeatOne = 1,
        Shuffle = 2,
    };

    struct MainWindow : MainWindowT<MainWindow>
    {
        MainWindow();

        void OnWindowLoaded(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnWindowSizeChanged(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::WindowSizeChangedEventArgs const&);
        void OnThemeCycleClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnSettingsFlyoutOpening(Windows::Foundation::IInspectable const&, Windows::Foundation::IInspectable const&);
        void OnSettingsSaveClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnQrRefreshClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnLoginMethodSelectionChanged(
            Windows::Foundation::IInspectable const& sender,
            Windows::Foundation::IInspectable const& args);
        void OnGuestLoginClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnPhoneLoginClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnCaptchaSendClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnLogoutClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnSidebarNavSelectionChanged(
            Windows::Foundation::IInspectable const& sender,
            Microsoft::UI::Xaml::Controls::SelectionChangedEventArgs const& args);
        void OnPlaylistListContainerContentChanging(
            Microsoft::UI::Xaml::Controls::ListViewBase const& sender,
            Microsoft::UI::Xaml::Controls::ContainerContentChangingEventArgs const& args);
        void OnPlaylistListSelectionChanged(Windows::Foundation::IInspectable const& sender, Microsoft::UI::Xaml::Controls::SelectionChangedEventArgs const& e);
        void OnTrackListContainerContentChanging(
            Microsoft::UI::Xaml::Controls::ListViewBase const& sender,
            Microsoft::UI::Xaml::Controls::ContainerContentChangingEventArgs const& args);
        void OnTrackListItemClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::Controls::ItemClickEventArgs const& args);
        void OnPlayerPlayPauseClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnPlayerPrevClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnPlayerNextClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnPlayerModeClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnSeekSliderPointerPressed(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::Input::PointerRoutedEventArgs const&);
        void OnSeekSliderPointerReleased(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::Input::PointerRoutedEventArgs const&);
        void OnPlayerVolumeValueChanged(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::Controls::Primitives::RangeBaseValueChangedEventArgs const& e);
        void OnNowPlayingBackClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnHomeMiniPlayPauseClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnHomeMiniOpenPlayerClick(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::RoutedEventArgs const&);
        void OnQrTimerTick(Windows::Foundation::IInspectable const&, Windows::Foundation::IInspectable const&);
        void OnCaptchaCooldownTick(Windows::Foundation::IInspectable const&, Windows::Foundation::IInspectable const&);
        void OnSystemColorsChanged(
            Windows::UI::ViewManagement::UISettings const&,
            Windows::Foundation::IInspectable const&);
        void OnWindowClosed(Windows::Foundation::IInspectable const&, Microsoft::UI::Xaml::WindowEventArgs const&);

    private:
        void ApplyThemeFromSettings();
        void UpdateTitleBarChrome();
        void ShowLoginUi(bool startQrLoad);
        void ShowMainUi(SessionData const& session);
        void HideLoginError();
        void ShowLoginError(hstring const& message);
        void ApplyLoginMethodPanes(bool loadQrIfShowingQr);
        Windows::Foundation::IAsyncAction RunStartupAsync();
        Windows::Foundation::IAsyncAction LoadQrAsync();
        void StartQrPolling();
        void StopQrTimerOnly();
        void StopQrPolling();
        void StartCaptchaCooldown(int seconds);
        void ResetCaptchaCooldownUi();
        void UpdateCaptchaSendButtonLabel();
        Windows::Foundation::IAsyncAction CheckQrOnceAsync();
        Windows::Foundation::IAsyncAction SetQrImageFromBase64Async(hstring const& base64);
        Windows::Foundation::IAsyncAction GuestLoginAsync();
        Windows::Foundation::IAsyncAction PhoneLoginAsync();
        Windows::Foundation::IAsyncAction CaptchaSendAsync();
        Windows::Foundation::IAsyncAction LogoutAsync();
        Windows::Foundation::IAsyncAction RefreshLibraryAsync();
        Windows::Foundation::IAsyncAction LoadPlaylistTracksAsync(int64_t playlistId, hstring const& title);
        void RequestLibraryAutoRefresh();
        void ApplyTrackListToUi(hstring const& title);
        void ClearTrackListData();
        void ResetLibraryUiState();

        void EnsureMediaPlayer();
        void ShutdownMediaPlayer();
        void StopPlaybackHard();
        void BeginPlaybackAt(uint32_t queueIndex);
        Windows::Foundation::IAsyncAction LoadAndPlayCoreAsync(uint32_t queueIndex, uint64_t requestId);
        void OnPlayUiTick(Windows::Foundation::IInspectable const&, Windows::Foundation::IInspectable const&);
        void OnPlayerMediaEnded(winrt::Windows::Media::Playback::MediaPlayer const&, Windows::Foundation::IInspectable const&);
        void OnPlayerMediaFailed(winrt::Windows::Media::Playback::MediaPlayer const&, winrt::Windows::Media::Playback::MediaPlayerFailedEventArgs const& args);
        void OnSessionPlaybackStateChanged(winrt::Windows::Media::Playback::MediaPlaybackSession const&, Windows::Foundation::IInspectable const&);
        void OnSessionNaturalDurationChanged(winrt::Windows::Media::Playback::MediaPlaybackSession const&, Windows::Foundation::IInspectable const&);
        void UpdatePlaybackUiFromSession();
        void UpdateLyricUiForPositionMs(int64_t positionMs);
        void ClearLyricUi();
        void ClearPlayerCoverUi();
        void UpdatePlayPauseGlyph(bool isPlaying);
        void UpdateHomeMiniPlayGlyph(bool isPlaying);
        void SetNowPlayingOverlayVisible(bool visible);
        void SyncHomeMiniPlayerUi();
        void UpdateModeButtonLabel();
        void HandleTrackEnded();
        void SkipToPrevious();
        void SkipToNext();
        int32_t PickShuffleIndex(int32_t currentIndex);

        Microsoft::UI::Xaml::DispatcherTimer m_qrTimer{ nullptr };
        Microsoft::UI::Xaml::DispatcherTimer m_captchaCooldownTimer{ nullptr };
        hstring m_qrUnikey;
        std::atomic_bool m_qrTickBusy{ false };
        int m_captchaCooldownSec{};
        Windows::UI::ViewManagement::UISettings m_uiSettings{ nullptr };
        SessionData m_activeSession{};
        bool m_suppressLoginMethodSelection{};
        std::vector<winrt::ZMusic::LibraryPlaylistRow> m_playlistRows;
        std::optional<int64_t> m_libraryUid;
        std::optional<int64_t> m_heartPlaylistId;
        uint32_t m_likeCount{};
        std::vector<winrt::ZMusic::LibraryTrackRow> m_trackRows;
        bool m_suppressPlaylistSelection{};
        bool m_suppressSidebarNav{};
        std::atomic_bool m_libraryRefreshInFlight{};

        winrt::Windows::Media::Playback::MediaPlayer m_player{ nullptr };
        winrt::Windows::Media::Playback::MediaPlayer::MediaEnded_revoker m_mediaEndedRevoker{};
        winrt::Windows::Media::Playback::MediaPlayer::MediaFailed_revoker m_mediaFailedRevoker{};
        winrt::Windows::Media::Playback::MediaPlaybackSession::PlaybackStateChanged_revoker m_sessionStateRevoker{};
        winrt::Windows::Media::Playback::MediaPlaybackSession::NaturalDurationChanged_revoker m_sessionDurationRevoker{};
        Microsoft::UI::Xaml::DispatcherTimer m_playUiTimer{ nullptr };
        std::vector<winrt::ZMusic::LibraryTrackRow> m_playbackQueue;
        int32_t m_queueIndex{ -1 };
        std::vector<winrt::ZMusic::LrcLine> m_lyricLines;
        LibraryPlaybackMode m_playbackMode{ LibraryPlaybackMode::Order };
        std::mt19937 m_shuffleRng;
        std::atomic_uint64_t m_playRequestId{ 0 };
        bool m_seekDragging{};
        bool m_suppressSeekSlider{};
        bool m_suppressVolumeEvents{};
    };
}

namespace winrt::ZMusic::factory_implementation
{
    struct MainWindow : MainWindowT<MainWindow, implementation::MainWindow>
    {
    };
}
