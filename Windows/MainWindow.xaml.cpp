#include "pch.h"
#include "MainWindow.xaml.h"
#include "CacheStore.h"
#include "CryptoMd5.h"
#include "LrcParser.h"
#include "NcmClient.h"
#include "NcmJson.h"
#include "NcmPlayback.h"
#if __has_include("MainWindow.g.cpp")
#include "MainWindow.g.cpp"
#endif

#include <Microsoft.UI.Xaml.Window.h>
#include <wil/resource.h>

#include <cstddef>
#include <random>
#include <thread>

using namespace winrt;
using namespace winrt::Windows::Foundation;
using namespace winrt::Windows::Storage::Streams;
using namespace winrt::Windows::Security::Cryptography;
using namespace winrt::Windows::UI::ViewManagement;
using namespace winrt::Microsoft::UI::Dispatching;
using namespace winrt::Microsoft::UI::Xaml;
using namespace winrt::Microsoft::UI::Xaml::Controls;
using namespace winrt::Microsoft::UI::Xaml::Media;
using namespace winrt::Microsoft::UI::Xaml::Media::Imaging;
using namespace winrt::Microsoft::UI::Xaml::Input;
using namespace winrt::Microsoft::UI::Xaml::Controls::Primitives;
using namespace winrt::Microsoft::UI::Windowing;
using namespace winrt::Windows::Data::Json;
using namespace winrt::Windows::Media::Core;
using namespace winrt::Windows::Media::Playback;
using namespace winrt::Windows::Storage;

namespace
{
    winrt::fire_and_forget LoadPlayerCoverAsync(Image const coverImg, FontIcon const musicIcon, hstring const url)
    {
        DispatcherQueue const dq = coverImg.DispatcherQueue();
        bool ok = false;
        try
        {
            StorageFile const file = co_await winrt::ZMusic::CacheStore::EnsureImageFileForUrlAsync(url);
            IRandomAccessStreamWithContentType const stream = co_await file.OpenReadAsync();
            co_await wil::resume_foreground(dq);
            BitmapImage bmp;
            co_await bmp.SetSourceAsync(stream);
            coverImg.Source(bmp);
            coverImg.Visibility(Visibility::Visible);
            musicIcon.Visibility(Visibility::Collapsed);
            ok = true;
        }
        catch (...)
        {
        }
        if (!ok && dq)
        {
            co_await wil::resume_foreground(dq);
            coverImg.Visibility(Visibility::Collapsed);
            musicIcon.Visibility(Visibility::Visible);
        }
    }

    winrt::fire_and_forget LoadPlaylistCoverAsync(Image const coverImg, FontIcon const musicIcon, hstring const url)
    {
        DispatcherQueue const dq = coverImg.DispatcherQueue();
        bool ok = false;
        try
        {
            StorageFile const file = co_await winrt::ZMusic::CacheStore::EnsureImageFileForUrlAsync(url);
            IRandomAccessStreamWithContentType const stream = co_await file.OpenReadAsync();
            co_await wil::resume_foreground(dq);
            BitmapImage bmp;
            co_await bmp.SetSourceAsync(stream);
            coverImg.Source(bmp);
            coverImg.Visibility(Visibility::Visible);
            musicIcon.Visibility(Visibility::Collapsed);
            ok = true;
        }
        catch (...)
        {
        }
        if (!ok && dq)
        {
            co_await wil::resume_foreground(dq);
            coverImg.Visibility(Visibility::Collapsed);
            musicIcon.Visibility(Visibility::Visible);
        }
    }

    hstring TrimWs(hstring const& s)
    {
        std::wstring_view w{ s };
        while (!w.empty() && iswspace(w.front()))
        {
            w.remove_prefix(1);
        }
        while (!w.empty() && iswspace(w.back()))
        {
            w.remove_suffix(1);
        }
        return hstring{ w };
    }

    hstring ApiMessage(winrt::Windows::Data::Json::JsonObject const& j)
    {
        if (j.HasKey(L"msg"))
        {
            return j.GetNamedString(L"msg");
        }
        if (j.HasKey(L"message"))
        {
            return j.GetNamedString(L"message");
        }
        return L"请求失败";
    }

    hstring FormatDurationMs(int64_t const ms)
    {
        if (ms <= 0)
        {
            return L"";
        }
        int64_t const t = ms / 1000;
        int64_t const m = t / 60;
        int64_t const s = t % 60;
        std::wstring w = std::to_wstring(m);
        w += L':';
        if (s < 10)
        {
            w += L'0';
        }
        w += std::to_wstring(s);
        return hstring{ w };
    }
}

namespace winrt::ZMusic::implementation
{
    MainWindow::MainWindow() : m_shuffleRng(std::random_device{}())
    {
        // InitializeComponent 由 XAML 生成链路调用；不在此构造函数中调用。
    }

    void MainWindow::OnWindowLoaded(IInspectable const&, RoutedEventArgs const&)
    {
        try
        {
            SystemBackdrop(MicaBackdrop{});
        }
        catch (...)
        {
            try
            {
                SystemBackdrop(DesktopAcrylicBackdrop{});
            }
            catch (...)
            {
            }
        }
        ExtendsContentIntoTitleBar(true);
        SetTitleBar(TitleBarDragRegion());

        ApplyThemeFromSettings();
        UpdateTitleBarChrome();
        DispatcherQueue().TryEnqueue(
            DispatcherQueuePriority::Low,
            [this]()
            {
                UpdateTitleBarChrome();
            });

        m_uiSettings = UISettings{};
        m_uiSettings.ColorValuesChanged({ this, &MainWindow::OnSystemColorsChanged });

        Closed({ this, &MainWindow::OnWindowClosed });

        try
        {
            EnsureMediaPlayer();
        }
        catch (...)
        {
        }

        // 必须延续 IAsyncAction 生命周期，避免丢弃返回值导致启动协程未执行完毕。
        auto const life = get_strong();
        [life]() -> winrt::fire_and_forget { co_await life->RunStartupAsync(); }();
    }

    void MainWindow::OnWindowClosed(IInspectable const&, WindowEventArgs const&)
    {
        ShutdownMediaPlayer();
        []() -> winrt::fire_and_forget { co_await winrt::ZMusic::CacheStore::ClearAllAsync(); }();
    }

    void MainWindow::OnSystemColorsChanged(UISettings const&, IInspectable const&)
    {
        if (AppSettings::Theme() != ThemePreference::System)
        {
            return;
        }
        DispatcherQueue().TryEnqueue(
            DispatcherQueuePriority::Normal,
            [this]()
            {
                ApplyThemeFromSettings();
            });
    }

    void MainWindow::ApplyThemeFromSettings()
    {
        auto const t = AppSettings::Theme();
        ElementTheme et = ElementTheme::Default;
        if (t == ThemePreference::Light)
        {
            et = ElementTheme::Light;
        }
        else if (t == ThemePreference::Dark)
        {
            et = ElementTheme::Dark;
        }
        if (auto fe = Content().try_as<FrameworkElement>())
        {
            fe.RequestedTheme(et);
        }
        UpdateTitleBarChrome();
    }

    void MainWindow::UpdateTitleBarChrome()
    {
        try
        {
            HWND hwnd = nullptr;
            auto const wndNative = this->try_as<IWindowNative>();
            if (!wndNative || FAILED(wndNative->get_WindowHandle(&hwnd)) || !hwnd)
            {
                return;
            }
            Microsoft::UI::WindowId const wid = Microsoft::UI::GetWindowIdFromWindow(hwnd);
            Microsoft::UI::Windowing::AppWindow const aw = Microsoft::UI::Windowing::AppWindow::GetFromWindowId(wid);
            Microsoft::UI::Windowing::AppWindowTitleBar bar = aw.TitleBar();

            double const padLeft = 16.0 + static_cast<double>(bar.LeftInset());
            double const padRight = 10.0 + static_cast<double>(bar.RightInset());
            AppTitleBar().Padding(Thickness{ padLeft, 0.0, padRight, 0.0 });

            auto const root = Content().try_as<FrameworkElement>();
            if (!root)
            {
                return;
            }
            bool const dark = root.ActualTheme() == ElementTheme::Dark;
            bar.ButtonForegroundColor(dark ? Windows::UI::Colors::White() : Windows::UI::Colors::Black());
            bar.ButtonHoverForegroundColor(dark ? Windows::UI::Colors::White() : Windows::UI::Colors::Black());
            bar.ButtonPressedForegroundColor(dark ? Windows::UI::Colors::White() : Windows::UI::Colors::Black());
            bar.ButtonInactiveForegroundColor(
                dark ? Windows::UI::Color{ 255, 160, 160, 160 } : Windows::UI::Color{ 255, 96, 96, 96 });
        }
        catch (...)
        {
        }
    }

    void MainWindow::OnWindowSizeChanged(IInspectable const&, WindowSizeChangedEventArgs const&)
    {
        UpdateTitleBarChrome();
    }

    void MainWindow::OnThemeCycleClick(IInspectable const&, RoutedEventArgs const&)
    {
        ThemePreference next = ThemePreference::System;
        auto const cur = AppSettings::Theme();
        if (cur == ThemePreference::System)
        {
            next = ThemePreference::Light;
        }
        else if (cur == ThemePreference::Light)
        {
            next = ThemePreference::Dark;
        }
        else
        {
            next = ThemePreference::System;
        }
        AppSettings::Theme(next);
        ApplyThemeFromSettings();
    }

    void MainWindow::OnSettingsFlyoutOpening(IInspectable const&, IInspectable const&)
    {
        SettingsApiUrlBox().Text(AppSettings::ApiBaseUrl());
        int32_t const idx = static_cast<int32_t>(AppSettings::Theme());
        SettingsThemeCombo().SelectedIndex((std::max)(0, (std::min)(2, idx)));
    }

    void MainWindow::OnSettingsSaveClick(IInspectable const&, RoutedEventArgs const&)
    {
        hstring const url = TrimWs(SettingsApiUrlBox().Text());
        if (!url.empty())
        {
            AppSettings::ApiBaseUrl(url);
        }
        int32_t const sel = SettingsThemeCombo().SelectedIndex();
        if (sel >= 0)
        {
            AppSettings::Theme(static_cast<ThemePreference>(sel));
        }
        ApplyThemeFromSettings();
        SettingsFlyout().Hide();
    }

    void MainWindow::HideLoginError()
    {
        LoginErrorBar().IsOpen(false);
    }

    void MainWindow::ShowLoginError(hstring const& message)
    {
        LoginErrorBar().Message(message);
        LoginErrorBar().IsOpen(true);
    }

    void MainWindow::ApplyLoginMethodPanes(bool loadQrIfShowingQr)
    {
        bool const qrSelected = (LoginMethodRadio().SelectedIndex() == 0);
        LoginQrSection().Visibility(qrSelected ? Visibility::Visible : Visibility::Collapsed);
        LoginPhoneSection().Visibility(qrSelected ? Visibility::Collapsed : Visibility::Visible);
        if (qrSelected)
        {
            if (loadQrIfShowingQr)
            {
                LoadQrAsync();
            }
        }
        else
        {
            StopQrPolling();
        }
    }

    void MainWindow::OnLoginMethodSelectionChanged(IInspectable const& sender, IInspectable const&)
    {
        if (m_suppressLoginMethodSelection)
        {
            return;
        }
        auto const rb = sender.as<RadioButtons>();
        ApplyLoginMethodPanes(rb.SelectedIndex() == 0);
    }

    void MainWindow::ShowLoginUi(bool startQrLoad)
    {
        StopPlaybackHard();
        ResetCaptchaCooldownUi();
        SplashPanel().Visibility(Visibility::Collapsed);
        LoginPanel().Visibility(Visibility::Visible);
        MainPanel().Visibility(Visibility::Collapsed);
        HideLoginError();
        ResetLibraryUiState();
        m_suppressLoginMethodSelection = true;
        LoginMethodRadio().SelectedIndex(0);
        m_suppressLoginMethodSelection = false;
        ApplyLoginMethodPanes(startQrLoad);
    }

    void MainWindow::ShowMainUi(SessionData const& session)
    {
        SessionData local = session;
        auto apply = [this, local]()
        {
            m_activeSession = local;
            SplashPanel().Visibility(Visibility::Collapsed);
            LoginPanel().Visibility(Visibility::Collapsed);
            MainPanel().Visibility(Visibility::Visible);
            StopQrPolling();
            hstring welcome = L"已登录";
            if (!local.DisplayLabel.empty())
            {
                welcome = L"欢迎，" + local.DisplayLabel;
            }
            WelcomeText().Text(welcome);
            MainHomePane().Visibility(Visibility::Visible);
            MainLibraryPane().Visibility(Visibility::Collapsed);
            m_suppressSidebarNav = true;
            SidebarNavList().SelectedIndex(0);
            m_suppressSidebarNav = false;
            ResetLibraryUiState();
        };

        auto dq = DispatcherQueue();
        if (dq && dq.TryEnqueue(DispatcherQueuePriority::Normal, apply))
        {
            return;
        }
        apply();
    }

    void MainWindow::OnSidebarNavSelectionChanged(IInspectable const&, SelectionChangedEventArgs const&)
    {
        if (m_suppressSidebarNav)
        {
            return;
        }
        auto const item = SidebarNavList().SelectedItem().try_as<ListBoxItem>();
        if (!item)
        {
            return;
        }
        hstring const tag = winrt::unbox_value_or<hstring>(item.Tag(), L"home");
        bool const home = (tag == L"home");
        MainHomePane().Visibility(home ? Visibility::Visible : Visibility::Collapsed);
        MainLibraryPane().Visibility(home ? Visibility::Collapsed : Visibility::Visible);
        if (!home)
        {
            RequestLibraryAutoRefresh();
        }
    }

    void MainWindow::RequestLibraryAutoRefresh()
    {
        if (m_activeSession.IsGuest)
        {
            LibraryPaneHintText().Text(L"登录后可同步个人歌单");
            LibraryMessageText().Text(L"");
            LibraryMessageText().Visibility(Visibility::Collapsed);
            return;
        }
        RefreshLibraryAsync();
    }

    void MainWindow::ClearTrackListData()
    {
        m_trackRows.clear();
        TrackListView().Items().Clear();
        LibraryTracksRing().Visibility(Visibility::Collapsed);
        LibraryTracksEmptyText().Visibility(Visibility::Visible);
        LibraryTracksEmptyText().Text(L"暂无曲目");
        TrackListView().Visibility(Visibility::Collapsed);
    }

    void MainWindow::ApplyTrackListToUi(hstring const& title)
    {
        LibraryDetailTitle().Text(title);
        if (m_trackRows.empty())
        {
            LibraryTracksEmptyText().Visibility(Visibility::Visible);
            LibraryTracksEmptyText().Text(L"暂无曲目");
            TrackListView().Visibility(Visibility::Collapsed);
            LibraryDetailSubtitle().Text(L"该歌单暂无曲目。");
            return;
        }
        LibraryTracksEmptyText().Visibility(Visibility::Collapsed);
        TrackListView().Visibility(Visibility::Visible);
        LibraryDetailSubtitle().Text(hstring{ std::to_wstring(m_trackRows.size()) + L" 首 · 点击曲目进入全屏播放" });
        TrackListView().Items().Clear();
        for (size_t i = 0; i < m_trackRows.size(); ++i)
        {
            TrackListView().Items().Append(box_value(static_cast<uint32_t>(i)));
        }
    }

    void MainWindow::ResetLibraryUiState()
    {
        m_playlistRows.clear();
        m_libraryUid.reset();
        m_heartPlaylistId.reset();
        m_likeCount = 0;
        PlaylistListView().Items().Clear();
        LibraryDetailTitle().Text(L"曲目");
        LibraryDetailSubtitle().Text(L"选择左侧歌单");
        LibraryPaneHintText().Text(L"进入此页时自动同步");
        LibraryMessageText().Text(L"");
        LibraryMessageText().Visibility(Visibility::Collapsed);
        ClearTrackListData();
    }

    IAsyncAction MainWindow::LoadPlaylistTracksAsync(int64_t playlistId, hstring const& title)
    {
        auto const life = get_strong();
        if (playlistId <= 0)
        {
            co_return;
        }
        co_await wil::resume_foreground(DispatcherQueue());
        ClearTrackListData();
        LibraryDetailTitle().Text(title);
        LibraryDetailSubtitle().Text(L"正在加载…");
        LibraryTracksRing().Visibility(Visibility::Visible);
        LibraryTracksEmptyText().Visibility(Visibility::Collapsed);
        TrackListView().Visibility(Visibility::Visible);

        bool loadFailed = false;
        try
        {
            co_await winrt::resume_background();
            NcmClient client{ AppSettings::ApiBaseUrl() };
            std::wstring const cookieW{ m_activeSession.Cookie.c_str() };
            JsonObject const pd = co_await client.GetAsync(
                L"/playlist/detail",
                {
                    { L"id", std::to_wstring(playlistId) },
                    { L"limit", L"1000" },
                    { L"cookie", cookieW },
                },
                true);
            std::vector<winrt::ZMusic::LibraryTrackRow> tracks = NcmLibrary::TracksFromPlaylistDetail(pd);
            if (tracks.empty())
            {
                std::vector<int64_t> const idOnly = NcmLibrary::TrackIdsFromPlaylistDetail(pd);
                static constexpr size_t kChunk = 80;
                for (size_t off = 0; off < idOnly.size(); off += kChunk)
                {
                    std::wstring idParam;
                    size_t const end = off + kChunk < idOnly.size() ? off + kChunk : idOnly.size();
                    for (size_t i = off; i < end; ++i)
                    {
                        if (!idParam.empty())
                        {
                            idParam += L',';
                        }
                        idParam += std::to_wstring(idOnly[i]);
                    }
                    JsonObject sd{ nullptr };
                    bool chunkOk = false;
                    for (int attempt = 0; attempt < 3; ++attempt)
                    {
                        try
                        {
                            sd = co_await client.GetAsync(
                                L"/song/detail",
                                {
                                    { L"ids", idParam },
                                    { L"cookie", cookieW },
                                },
                                true);
                            if (NcmJson::ApiCode(sd) == 200)
                            {
                                chunkOk = true;
                                break;
                            }
                        }
                        catch (...)
                        {
                        }
                        std::this_thread::sleep_for(std::chrono::milliseconds(200 * (attempt + 1)));
                    }
                    if (!chunkOk)
                    {
                        throw hresult_error{ E_FAIL };
                    }
                    auto const part = NcmLibrary::TracksFromSongDetail(sd);
                    tracks.insert(tracks.end(), part.begin(), part.end());
                    if (end < idOnly.size())
                    {
                        std::this_thread::sleep_for(std::chrono::milliseconds(45));
                    }
                }
            }
            m_trackRows = std::move(tracks);
        }
        catch (...)
        {
            loadFailed = true;
            m_trackRows.clear();
        }

        co_await wil::resume_foreground(DispatcherQueue());
        LibraryTracksRing().Visibility(Visibility::Collapsed);
        if (loadFailed)
        {
            LibraryDetailTitle().Text(title);
            LibraryDetailSubtitle().Text(L"加载失败，请检查网络或稍后重试。");
            ClearTrackListData();
            LibraryTracksEmptyText().Text(L"无法加载曲目列表");
            co_return;
        }
        ApplyTrackListToUi(title);
    }

    void MainWindow::OnPlaylistListContainerContentChanging(ListViewBase const&, ContainerContentChangingEventArgs const& args)
    {
        if (args.InRecycleQueue())
        {
            return;
        }
        int32_t const iidx = args.ItemIndex();
        if (iidx < 0 || static_cast<size_t>(iidx) >= m_playlistRows.size())
        {
            return;
        }
        if (args.Phase() == 0)
        {
            args.RegisterUpdateCallback({ this, &MainWindow::OnPlaylistListContainerContentChanging });
            return;
        }
        FrameworkElement const root = args.ItemContainer().ContentTemplateRoot().try_as<FrameworkElement>();
        if (!root)
        {
            return;
        }
        auto const& row = m_playlistRows[static_cast<size_t>(iidx)];
        if (auto tb = root.FindName(L"PlTitle").try_as<TextBlock>())
        {
            tb.Text(row.Name);
        }
        if (auto tb = root.FindName(L"PlSub").try_as<TextBlock>())
        {
            std::wstring sub = std::to_wstring(row.TrackCount) + L" 首";
            if (row.IsHeart)
            {
                sub += L" · 红心歌单";
            }
            else if (row.IsOwned)
            {
                sub += L" · 创建";
            }
            else if (row.IsSubscribed)
            {
                sub += L" · 收藏";
            }
            tb.Text(hstring{ sub });
        }
        if (auto img = root.FindName(L"PlCoverImage").try_as<Image>())
        {
            if (auto fi = root.FindName(L"PlMusicIcon").try_as<FontIcon>())
            {
                if (!row.CoverUrl.empty())
                {
                    img.Visibility(Visibility::Collapsed);
                    fi.Visibility(Visibility::Visible);
                    LoadPlaylistCoverAsync(img, fi, row.CoverUrl);
                }
                else
                {
                    img.Visibility(Visibility::Collapsed);
                    fi.Visibility(Visibility::Visible);
                }
            }
        }
    }

    void MainWindow::OnPlaylistListSelectionChanged(IInspectable const& sender, SelectionChangedEventArgs const&)
    {
        if (m_suppressPlaylistSelection)
        {
            return;
        }
        auto const lv = sender.as<ListView>();
        int32_t const idx = lv.SelectedIndex();
        if (idx < 0 || static_cast<size_t>(idx) >= m_playlistRows.size())
        {
            return;
        }
        auto const& row = m_playlistRows[static_cast<size_t>(idx)];
        LoadPlaylistTracksAsync(row.Id, row.Name);
    }

    void MainWindow::OnTrackListContainerContentChanging(ListViewBase const&, ContainerContentChangingEventArgs const& args)
    {
        if (args.InRecycleQueue())
        {
            return;
        }
        int32_t const iidx = args.ItemIndex();
        if (iidx < 0 || static_cast<size_t>(iidx) >= m_trackRows.size())
        {
            return;
        }
        if (args.Phase() == 0)
        {
            args.RegisterUpdateCallback({ this, &MainWindow::OnTrackListContainerContentChanging });
            return;
        }
        FrameworkElement const root = args.ItemContainer().ContentTemplateRoot().try_as<FrameworkElement>();
        if (!root)
        {
            return;
        }
        auto const& tr = m_trackRows[static_cast<size_t>(iidx)];
        if (auto tb = root.FindName(L"TrIdx").try_as<TextBlock>())
        {
            tb.Text(hstring{ std::to_wstring(static_cast<size_t>(iidx) + 1) });
        }
        if (auto tb = root.FindName(L"TrTitle").try_as<TextBlock>())
        {
            tb.Text(tr.Title);
        }
        if (auto tb = root.FindName(L"TrSub").try_as<TextBlock>())
        {
            hstring sub = tr.Artists;
            if (!tr.Album.empty())
            {
                sub = sub + L" · " + tr.Album;
            }
            tb.Text(sub);
        }
        if (auto tb = root.FindName(L"TrDur").try_as<TextBlock>())
        {
            tb.Text(FormatDurationMs(tr.DurationMs));
        }
    }

    void MainWindow::OnTrackListItemClick(IInspectable const&, ItemClickEventArgs const& args)
    {
        uint32_t idx = 0;
        try
        {
            idx = winrt::unbox_value<uint32_t>(args.ClickedItem());
        }
        catch (...)
        {
            return;
        }
        if (static_cast<size_t>(idx) >= m_trackRows.size())
        {
            return;
        }
        m_playbackQueue = m_trackRows;
        SetNowPlayingOverlayVisible(true);
        BeginPlaybackAt(idx);
    }

    IAsyncAction MainWindow::RefreshLibraryAsync()
    {
        auto const life = get_strong();
        if (m_libraryRefreshInFlight.exchange(true))
        {
            co_return;
        }
        auto const endRefresh = wil::scope_exit([this]()
                                                {
                                                    m_libraryRefreshInFlight.store(false);
                                                    LibraryBusyRing().IsActive(false);
                                                    LibraryBusyRing().Visibility(Visibility::Collapsed);
                                                });

        ResetLibraryUiState();
        LibraryBusyRing().Visibility(Visibility::Visible);
        LibraryBusyRing().IsActive(true);

        bool refreshHadError = false;
        try
        {
            co_await winrt::resume_background();
            NcmClient client{ AppSettings::ApiBaseUrl() };
            std::map<std::wstring, std::wstring> const cookieQ{
                { L"cookie", std::wstring{ m_activeSession.Cookie.c_str() } },
            };
            JsonObject const status = co_await client.GetAsync(L"/login/status", cookieQ);
            co_await wil::resume_foreground(DispatcherQueue());

            if (m_activeSession.IsGuest)
            {
                LibraryPaneHintText().Text(L"登录后可同步个人歌单");
                LibraryMessageText().Text(L"游客模式无法拉取个人歌单。");
                LibraryMessageText().Visibility(Visibility::Visible);
                co_return;
            }

            auto const uidOpt = NcmJson::UserIdFromLoginStatus(status);
            if (!uidOpt)
            {
                LibraryPaneHintText().Text(L"登录状态异常");
                LibraryMessageText().Text(L"无法解析用户 id，请重新登录。");
                LibraryMessageText().Visibility(Visibility::Visible);
                co_return;
            }

            int64_t const uidVal = *uidOpt;
            std::wstring const uidStr = std::to_wstring(uidVal);
            std::wstring const cookieW{ m_activeSession.Cookie.c_str() };

            co_await winrt::resume_background();
            JsonObject const pl = co_await client.GetAsync(
                L"/user/playlist",
                {
                    { L"uid", uidStr },
                    { L"limit", L"80" },
                    { L"offset", L"0" },
                    { L"cookie", cookieW },
                });
            co_await wil::resume_foreground(DispatcherQueue());

            if (NcmJson::ApiCode(pl) != 200)
            {
                LibraryPaneHintText().Text(L"同步未完成");
                LibraryMessageText().Text(ApiMessage(pl));
                LibraryMessageText().Visibility(Visibility::Visible);
                co_return;
            }

            if (!pl.HasKey(L"playlist"))
            {
                LibraryPaneHintText().Text(L"同步未完成");
                LibraryMessageText().Text(L"响应中无歌单数据。");
                LibraryMessageText().Visibility(Visibility::Visible);
                co_return;
            }

            m_libraryUid = uidVal;
            NcmLibrary::PlaylistsFromUserPlaylist(pl, uidVal, m_playlistRows);
            m_heartPlaylistId.reset();
            for (auto const& r : m_playlistRows)
            {
                if (r.IsHeart)
                {
                    m_heartPlaylistId = r.Id;
                    break;
                }
            }

            PlaylistListView().Items().Clear();
            for (size_t i = 0; i < m_playlistRows.size(); ++i)
            {
                PlaylistListView().Items().Append(box_value(static_cast<uint32_t>(i)));
            }

            uint32_t likeN = 0;
            co_await winrt::resume_background();
            try
            {
                JsonObject const likeJson = co_await client.GetAsync(
                    L"/likelist",
                    {
                        { L"uid", uidStr },
                        { L"cookie", cookieW },
                    });
                likeN = NcmLibrary::LikeIdsCount(likeJson);
            }
            catch (...)
            {
            }

            co_await wil::resume_foreground(DispatcherQueue());
            m_likeCount = likeN;

            m_suppressPlaylistSelection = true;
            PlaylistListView().SelectedIndex(-1);
            m_suppressPlaylistSelection = false;
            if (!m_playlistRows.empty())
            {
                PlaylistListView().SelectedIndex(0);
            }

            LibraryMessageText().Visibility(Visibility::Collapsed);
            LibraryMessageText().Text(L"");
            std::wstring hint = L"已同步";
            if (!m_playlistRows.empty())
            {
                hint += L" · " + std::to_wstring(m_playlistRows.size()) + L" 张歌单";
            }
            if (likeN > 0)
            {
                hint += L" · " + std::to_wstring(likeN) + L" 首红心";
            }
            LibraryPaneHintText().Text(hstring{ hint });
        }
        catch (...)
        {
            refreshHadError = true;
        }
        if (refreshHadError)
        {
            co_await wil::resume_foreground(DispatcherQueue());
            LibraryPaneHintText().Text(L"同步失败");
            LibraryMessageText().Text(L"加载失败，请检查网络与 API 基址。");
            LibraryMessageText().Visibility(Visibility::Visible);
        }
    }

    IAsyncAction MainWindow::RunStartupAsync()
    {
        auto const life = get_strong();
        SplashStatusText().Text(L"正在启动…");
        co_await winrt::resume_background();

        SessionData session{};
        try
        {
            session = SessionStore::ParseFromJson(co_await SessionStore::LoadRawAsync());
        }
        catch (...)
        {
            session = {};
        }

        co_await wil::resume_foreground(DispatcherQueue());

        if (!session.IsValid())
        {
            ShowLoginUi(true);
            co_return;
        }

        // 与 Android MainPlaceholderScreen 一致：游客信任本地 cookie，不做 /login/status 远程校验。
        if (session.IsGuest)
        {
            ShowMainUi(session);
            co_return;
        }

        bool validationError = false;
        try
        {
            SplashStatusText().Text(L"正在校验登录…");
            co_await wil::resume_foreground(DispatcherQueue());

            NcmClient client{ AppSettings::ApiBaseUrl() };
            co_await winrt::resume_background();
            JsonObject statusJson = co_await client.GetAsync(
                L"/login/status",
                { { L"cookie", std::wstring{ session.Cookie.c_str() } } });
            co_await wil::resume_foreground(DispatcherQueue());

            bool const loggedIn = NcmJson::IsLoggedInStatus(statusJson, false);
            int32_t const code = NcmJson::ApiCode(statusJson);
            if (loggedIn)
            {
                ShowMainUi(session);
            }
            else if (code == 200)
            {
                co_await SessionStore::ClearAsync();
                co_await wil::resume_foreground(DispatcherQueue());
                ShowLoginUi(true);
                ShowLoginError(L"登录已失效，请重新登录");
            }
            else
            {
                // 非 200（如 301/502）或形态异常：保留本地会话，避免误清（与 Android 乐观策略一致）
                ShowMainUi(session);
            }
        }
        catch (...)
        {
            validationError = true;
        }

        if (validationError)
        {
            co_await wil::resume_foreground(DispatcherQueue());
            ShowMainUi(session);
        }
    }

    void MainWindow::OnQrRefreshClick(IInspectable const&, RoutedEventArgs const&)
    {
        LoadQrAsync();
    }

    IAsyncAction MainWindow::LoadQrAsync()
    {
        auto const life = get_strong();
        HideLoginError();
        QrBusyRing().Visibility(Visibility::Visible);
        QrRefreshButton().IsEnabled(false);
        StopQrPolling();
        m_qrUnikey = L"";

        bool loadFailed = false;
        try
        {
            co_await winrt::resume_background();
            NcmClient client{ AppSettings::ApiBaseUrl() };
            JsonObject keyJson = co_await client.GetAsync(L"/login/qr/key", {});
            co_await wil::resume_foreground(DispatcherQueue());

            if (NcmJson::ApiCode(keyJson) != 200)
            {
                ShowLoginError(ApiMessage(keyJson));
                co_return;
            }
            auto const keyOpt = NcmJson::QrKey(keyJson);
            if (!keyOpt)
            {
                ShowLoginError(L"无法解析二维码 key");
                co_return;
            }
            m_qrUnikey = *keyOpt;

            co_await winrt::resume_background();
            JsonObject createJson = co_await client.GetAsync(
                L"/login/qr/create",
                { { L"key", std::wstring{ m_qrUnikey.c_str() } }, { L"qrimg", L"true" } });
            co_await wil::resume_foreground(DispatcherQueue());

            if (NcmJson::ApiCode(createJson) != 200)
            {
                ShowLoginError(ApiMessage(createJson));
                co_return;
            }
            auto const imgOpt = NcmJson::QrImgBase64(createJson);
            if (!imgOpt)
            {
                ShowLoginError(L"二维码数据为空");
                co_return;
            }
            co_await SetQrImageFromBase64Async(*imgOpt);
            QrHintText().Text(L"使用网易云音乐 App 扫描");
            StartQrPolling();
        }
        catch (...)
        {
            loadFailed = true;
        }

        co_await wil::resume_foreground(DispatcherQueue());
        if (loadFailed)
        {
            ShowLoginError(L"加载二维码失败");
        }
        QrBusyRing().Visibility(Visibility::Collapsed);
        QrRefreshButton().IsEnabled(true);
    }

    IAsyncAction MainWindow::SetQrImageFromBase64Async(hstring const& base64)
    {
        IBuffer const buf = CryptographicBuffer::DecodeFromBase64String(base64);
        InMemoryRandomAccessStream stream;
        IOutputStream out = stream.GetOutputStreamAt(0);
        DataWriter writer(out);
        writer.WriteBuffer(buf);
        co_await writer.StoreAsync();
        co_await out.FlushAsync();
        stream.Seek(0);

        co_await wil::resume_foreground(DispatcherQueue());
        BitmapImage bmp;
        bmp.SetSource(stream);
        QrImage().Source(bmp);
    }

    void MainWindow::StopQrTimerOnly()
    {
        if (m_qrTimer)
        {
            m_qrTimer.Stop();
            m_qrTimer = nullptr;
        }
    }

    void MainWindow::StartQrPolling()
    {
        StopQrTimerOnly();
        m_qrTimer = DispatcherTimer();
        m_qrTimer.Interval(std::chrono::seconds(2));
        m_qrTimer.Tick({ this, &MainWindow::OnQrTimerTick });
        m_qrTimer.Start();
    }

    void MainWindow::StopQrPolling()
    {
        StopQrTimerOnly();
        m_qrUnikey = L"";
    }

    void MainWindow::ResetCaptchaCooldownUi()
    {
        if (m_captchaCooldownTimer)
        {
            m_captchaCooldownTimer.Stop();
            m_captchaCooldownTimer = nullptr;
        }
        m_captchaCooldownSec = 0;
        CaptchaSendButton().IsEnabled(true);
        CaptchaSendButton().Content(box_value(hstring{ L"获取验证码" }));
    }

    void MainWindow::UpdateCaptchaSendButtonLabel()
    {
        if (m_captchaCooldownSec <= 0)
        {
            CaptchaSendButton().Content(box_value(hstring{ L"获取验证码" }));
            return;
        }
        CaptchaSendButton().Content(
            box_value(hstring{ std::to_wstring(m_captchaCooldownSec) + L" 秒后可重发" }));
    }

    void MainWindow::StartCaptchaCooldown(int seconds)
    {
        ResetCaptchaCooldownUi();
        m_captchaCooldownSec = seconds;
        CaptchaSendButton().IsEnabled(false);
        UpdateCaptchaSendButtonLabel();
        m_captchaCooldownTimer = DispatcherTimer();
        m_captchaCooldownTimer.Interval(std::chrono::seconds(1));
        m_captchaCooldownTimer.Tick({ this, &MainWindow::OnCaptchaCooldownTick });
        m_captchaCooldownTimer.Start();
    }

    void MainWindow::OnCaptchaCooldownTick(IInspectable const&, IInspectable const&)
    {
        m_captchaCooldownSec--;
        if (m_captchaCooldownSec <= 0)
        {
            ResetCaptchaCooldownUi();
            return;
        }
        UpdateCaptchaSendButtonLabel();
    }

    void MainWindow::OnCaptchaSendClick(IInspectable const&, RoutedEventArgs const&)
    {
        CaptchaSendAsync();
    }

    IAsyncAction MainWindow::CaptchaSendAsync()
    {
        auto const life = get_strong();
        HideLoginError();

        hstring const phone = TrimWs(PhoneBox().Text());
        if (phone.empty())
        {
            ShowLoginError(L"请先填写手机号");
            co_return;
        }

        bool sendFailed = false;
        try
        {
            co_await winrt::resume_background();
            NcmClient client{ AppSettings::ApiBaseUrl() };
            JsonObject j = co_await client.GetAsync(
                L"/captcha/sent",
                {
                    { L"phone", std::wstring{ phone.c_str() } },
                    { L"ctcode", L"86" },
                });
            co_await wil::resume_foreground(DispatcherQueue());

            if (NcmJson::ApiCode(j) != 200)
            {
                ShowLoginError(ApiMessage(j));
                co_return;
            }
            StartCaptchaCooldown(60);
        }
        catch (...)
        {
            sendFailed = true;
        }

        if (sendFailed)
        {
            co_await wil::resume_foreground(DispatcherQueue());
            ShowLoginError(L"发送验证码失败");
        }
    }

    void MainWindow::OnQrTimerTick(IInspectable const&, IInspectable const&)
    {
        if (m_qrUnikey.empty() || m_qrTickBusy.exchange(true))
        {
            return;
        }
        CheckQrOnceAsync();
    }

    IAsyncAction MainWindow::CheckQrOnceAsync()
    {
        struct ReleaseFlag
        {
            std::atomic_bool& b;
            ~ReleaseFlag()
            {
                b = false;
            }
        } release{ m_qrTickBusy };

        auto const life = get_strong();
        hstring const key = m_qrUnikey;
        if (key.empty())
        {
            co_return;
        }

        bool pollFailed = false;
        try
        {
            co_await winrt::resume_background();
            NcmClient client{ AppSettings::ApiBaseUrl() };
            JsonObject j = co_await client.GetAsync(L"/login/qr/check", { { L"key", std::wstring{ key.c_str() } } });
            int32_t code = NcmJson::QrCheckCode(j);
            if (code == 502)
            {
                j = co_await client.GetAsync(
                    L"/login/qr/check",
                    { { L"key", std::wstring{ key.c_str() } }, { L"noCookie", L"true" } });
                code = NcmJson::QrCheckCode(j);
            }
            co_await wil::resume_foreground(DispatcherQueue());

            // 803 分支含 co_await：单独处理，避免 switch+coroutine 边界问题；网关也可能返回 code=200 + data.code=803。
            if (code == 803)
            {
                auto const ck = NcmJson::ExtractCookie(j);
                if (!ck)
                {
                    ShowLoginError(L"登录成功但未返回 cookie");
                    StopQrPolling();
                }
                else
                {
                    SessionData sd;
                    sd.Cookie = *ck;
                    sd.DisplayLabel = NcmJson::DisplayLabelFromLogin(j).value_or(L"");
                    sd.IsGuest = false;
                    co_await SessionStore::SaveAsync(sd);
                    co_await wil::resume_foreground(DispatcherQueue());
                    StopQrPolling();
                    ShowMainUi(sd);
                }
            }
            else
            {
                switch (code)
                {
                case 800:
                    QrHintText().Text(L"二维码已过期，请刷新");
                    StopQrPolling();
                    break;
                case 801:
                    QrHintText().Text(L"等待扫描…");
                    break;
                case 802:
                    QrHintText().Text(L"请在手机上确认登录");
                    break;
                default:
                    break;
                }
            }
        }
        catch (...)
        {
            pollFailed = true;
        }

        if (pollFailed)
        {
            co_await wil::resume_foreground(DispatcherQueue());
            ShowLoginError(L"二维码轮询异常");
            StopQrPolling();
        }
    }

    namespace
    {
        int64_t TimeSpanToMs(TimeSpan const ts)
        {
            return static_cast<int64_t>(ts.count() / 10000LL);
        }
    } // namespace

    void MainWindow::EnsureMediaPlayer()
    {
        if (m_player)
        {
            return;
        }
        m_player = MediaPlayer();
        m_player.AudioCategory(MediaPlayerAudioCategory::Media);
        m_player.AutoPlay(false);
        m_mediaEndedRevoker = m_player.MediaEnded(winrt::auto_revoke, { this, &MainWindow::OnPlayerMediaEnded });
        m_mediaFailedRevoker = m_player.MediaFailed(winrt::auto_revoke, { this, &MainWindow::OnPlayerMediaFailed });
        MediaPlaybackSession const session = m_player.PlaybackSession();
        m_sessionStateRevoker =
            session.PlaybackStateChanged(winrt::auto_revoke, { this, &MainWindow::OnSessionPlaybackStateChanged });
        m_sessionDurationRevoker =
            session.NaturalDurationChanged(winrt::auto_revoke, { this, &MainWindow::OnSessionNaturalDurationChanged });

        m_suppressVolumeEvents = true;
        m_player.Volume(static_cast<double>(PlayerVolumeSlider().Value()) / 100.0);
        m_suppressVolumeEvents = false;

        m_playUiTimer = DispatcherTimer();
        m_playUiTimer.Interval(std::chrono::milliseconds{ 160 });
        m_playUiTimer.Tick({ this, &MainWindow::OnPlayUiTick });
        m_playUiTimer.Start();

        UpdateModeButtonLabel();
    }

    void MainWindow::ShutdownMediaPlayer()
    {
        ++m_playRequestId;
        if (m_playUiTimer)
        {
            m_playUiTimer.Stop();
        }
        m_mediaEndedRevoker = {};
        m_mediaFailedRevoker = {};
        m_sessionStateRevoker = {};
        m_sessionDurationRevoker = {};
        if (m_player)
        {
            try
            {
                m_player.Pause();
                m_player.Source(nullptr);
            }
            catch (...)
            {
            }
            try
            {
                m_player.Close();
            }
            catch (...)
            {
            }
            m_player = nullptr;
        }
        m_playbackQueue.clear();
        m_queueIndex = -1;
        m_lyricLines.clear();
        ClearLyricUi();
        ClearPlayerCoverUi();
    }

    void MainWindow::StopPlaybackHard()
    {
        ++m_playRequestId;
        m_playbackQueue.clear();
        m_queueIndex = -1;
        m_lyricLines.clear();
        ClearLyricUi();
        ClearPlayerCoverUi();
        PlayerErrorText().Visibility(Visibility::Collapsed);
        PlayerBufferingRing().Visibility(Visibility::Collapsed);
        PlayerBufferingRing().IsActive(false);
        SetNowPlayingOverlayVisible(false);
        HomeNowPlayingBar().Visibility(Visibility::Collapsed);
        HomeMiniTitleText().Text(L"");
        HomeMiniArtistText().Text(L"");
        UpdateHomeMiniPlayGlyph(false);
        PlayerTitleText().Text(L"未在播放");
        PlayerArtistText().Text(L"");
        m_suppressSeekSlider = true;
        PlayerSeekSlider().Value(0);
        PlayerSeekSlider().Maximum(1000);
        m_suppressSeekSlider = false;
        PlayerTimeText().Text(L"0:00 / 0:00");
        if (m_player)
        {
            try
            {
                m_player.Pause();
                m_player.Source(nullptr);
            }
            catch (...)
            {
            }
        }
        UpdatePlayPauseGlyph(false);
    }

    void MainWindow::BeginPlaybackAt(uint32_t const index)
    {
        EnsureMediaPlayer();
        if (m_playbackQueue.empty() || index >= m_playbackQueue.size())
        {
            return;
        }
        ++m_playRequestId;
        uint64_t const rid = m_playRequestId;
        m_queueIndex = static_cast<int32_t>(index);
        auto const self = get_strong();
        [self, index, rid]() -> winrt::fire_and_forget { co_await self->LoadAndPlayCoreAsync(index, rid); }();
    }

    IAsyncAction MainWindow::LoadAndPlayCoreAsync(uint32_t const index, uint64_t const requestId)
    {
        auto const life = get_strong();
        if (index >= m_playbackQueue.size())
        {
            co_return;
        }
        LibraryTrackRow const trackCopy = m_playbackQueue[index];

        co_await wil::resume_foreground(DispatcherQueue());
        if (requestId != m_playRequestId)
        {
            co_return;
        }
        PlayerErrorText().Visibility(Visibility::Collapsed);
        PlayerBufferingRing().Visibility(Visibility::Visible);
        PlayerBufferingRing().IsActive(true);
        UpdatePlayPauseGlyph(false);

        co_await winrt::resume_background();
        if (requestId != m_playRequestId)
        {
            co_return;
        }

        bool loadFailed = false;
        hstring failHint{};
        try
        {
            NcmClient client{ AppSettings::ApiBaseUrl() };
            std::wstring const cookieW{ m_activeSession.Cookie.c_str() };
            JsonObject const urlJson = co_await client.GetAsync(
                L"/song/url",
                {
                    { L"id", std::to_wstring(trackCopy.Id) },
                    { L"br", L"320000" },
                    { L"cookie", cookieW },
                });
            std::optional<hstring> urlOpt = NcmPlayback::SongUrlForTrackId(urlJson, trackCopy.Id);
            JsonObject lastUrlJson = urlJson;
            if (!urlOpt)
            {
                JsonObject const urlV1 = co_await client.GetAsync(
                    L"/song/url/v1",
                    {
                        { L"id", std::to_wstring(trackCopy.Id) },
                        { L"level", L"exhigh" },
                        { L"cookie", cookieW },
                    });
                lastUrlJson = urlV1;
                urlOpt = NcmPlayback::SongUrlForTrackId(urlV1, trackCopy.Id);
            }
            if (!urlOpt)
            {
                co_await wil::resume_foreground(DispatcherQueue());
                if (requestId == m_playRequestId)
                {
                    if (NcmJson::ApiCode(lastUrlJson) != 200)
                    {
                        PlayerErrorText().Text(ApiMessage(lastUrlJson));
                    }
                    else
                    {
                        PlayerErrorText().Text(L"暂无可用播放链接（可能受版权或账号权益限制）。");
                    }
                    PlayerErrorText().Visibility(Visibility::Visible);
                    PlayerBufferingRing().Visibility(Visibility::Collapsed);
                    PlayerBufferingRing().IsActive(false);
                }
                co_return;
            }

            std::vector<LrcLine> lines{};
            try
            {
                JsonObject const lyricJson = co_await client.GetAsync(
                    L"/lyric",
                    {
                        { L"id", std::to_wstring(trackCopy.Id) },
                        { L"cookie", cookieW },
                    });
                if (std::optional<hstring> const raw = NcmPlayback::LyricRawLrcText(lyricJson))
                {
                    lines = LrcParser::Parse(*raw);
                }
            }
            catch (...)
            {
            }

            if (requestId != m_playRequestId)
            {
                co_return;
            }

            co_await wil::resume_foreground(DispatcherQueue());
            if (requestId != m_playRequestId)
            {
                co_return;
            }

            m_lyricLines = std::move(lines);
            m_queueIndex = static_cast<int32_t>(index);
            PlayerTitleText().Text(trackCopy.Title);
            PlayerArtistText().Text(trackCopy.Artists);
            UpdateLyricUiForPositionMs(0);
            if (!trackCopy.CoverUrl.empty())
            {
                PlayerCoverImage().Visibility(Visibility::Collapsed);
                PlayerCoverPh().Visibility(Visibility::Visible);
                LoadPlayerCoverAsync(PlayerCoverImage(), PlayerCoverPh(), trackCopy.CoverUrl);
                HomeMiniCoverImage().Visibility(Visibility::Collapsed);
                HomeMiniCoverPh().Visibility(Visibility::Visible);
                LoadPlayerCoverAsync(HomeMiniCoverImage(), HomeMiniCoverPh(), trackCopy.CoverUrl);
            }
            else
            {
                ClearPlayerCoverUi();
            }

            m_player.Source(MediaSource::CreateFromUri(Uri(*urlOpt)));
            m_player.Play();
            PlayerBufferingRing().Visibility(Visibility::Collapsed);
            PlayerBufferingRing().IsActive(false);
            UpdatePlayPauseGlyph(true);
            SyncHomeMiniPlayerUi();
        }
        catch (winrt::hresult_error const& ex)
        {
            loadFailed = true;
            failHint = ex.message();
        }
        catch (...)
        {
            loadFailed = true;
        }
        if (loadFailed)
        {
            co_await wil::resume_foreground(DispatcherQueue());
            if (requestId == m_playRequestId)
            {
                PlayerErrorText().Text(failHint.empty() ? hstring{ L"加载失败，请检查网络与 API 基址。" } : failHint);
                PlayerErrorText().Visibility(Visibility::Visible);
                PlayerBufferingRing().Visibility(Visibility::Collapsed);
                PlayerBufferingRing().IsActive(false);
            }
        }
    }

    void MainWindow::ClearLyricUi()
    {
        PlayerLyricLineM2().Text(L"");
        PlayerLyricLineM1().Text(L"");
        PlayerLyricLine0().Text(L"");
        PlayerLyricLineP1().Text(L"");
        PlayerLyricLineP2().Text(L"");
    }

    void MainWindow::ClearPlayerCoverUi()
    {
        PlayerCoverImage().Source(nullptr);
        PlayerCoverImage().Visibility(Visibility::Collapsed);
        PlayerCoverPh().Visibility(Visibility::Visible);
        HomeMiniCoverImage().Source(nullptr);
        HomeMiniCoverImage().Visibility(Visibility::Collapsed);
        HomeMiniCoverPh().Visibility(Visibility::Visible);
    }

    void MainWindow::SetNowPlayingOverlayVisible(bool const visible)
    {
        NowPlayingOverlay().Visibility(visible ? Visibility::Visible : Visibility::Collapsed);
    }

    void MainWindow::SyncHomeMiniPlayerUi()
    {
        HomeMiniTitleText().Text(PlayerTitleText().Text());
        HomeMiniArtistText().Text(PlayerArtistText().Text());
        HomeNowPlayingBar().Visibility(Visibility::Visible);
    }

    void MainWindow::UpdateHomeMiniPlayGlyph(bool const isPlaying)
    {
        HomeMiniPlayPauseIcon().Glyph(isPlaying ? L"\uE103" : L"\uE102");
    }

    void MainWindow::OnNowPlayingBackClick(IInspectable const&, RoutedEventArgs const&)
    {
        SetNowPlayingOverlayVisible(false);
    }

    void MainWindow::OnHomeMiniPlayPauseClick(IInspectable const& sender, RoutedEventArgs const& e)
    {
        OnPlayerPlayPauseClick(sender, e);
    }

    void MainWindow::OnHomeMiniOpenPlayerClick(IInspectable const&, RoutedEventArgs const&)
    {
        SetNowPlayingOverlayVisible(true);
    }

    void MainWindow::UpdatePlayPauseGlyph(bool const isPlaying)
    {
        PlayerPlayPauseIcon().Glyph(isPlaying ? L"\uE103" : L"\uE102");
        UpdateHomeMiniPlayGlyph(isPlaying);
    }

    void MainWindow::UpdateModeButtonLabel()
    {
        hstring t = L"顺序";
        if (m_playbackMode == LibraryPlaybackMode::RepeatOne)
        {
            t = L"单曲";
        }
        else if (m_playbackMode == LibraryPlaybackMode::Shuffle)
        {
            t = L"随机";
        }
        PlayerModeButton().Content(box_value(t));
    }

    void MainWindow::UpdateLyricUiForPositionMs(int64_t const positionMs)
    {
        if (m_lyricLines.empty())
        {
            ClearLyricUi();
            return;
        }
        size_t cur = 0;
        for (size_t i = 0; i < m_lyricLines.size(); ++i)
        {
            if (m_lyricLines[i].timeMs <= positionMs)
            {
                cur = i;
            }
            else
            {
                break;
            }
        }
        auto const lineAt = [this](std::ptrdiff_t offset, std::ptrdiff_t center) -> hstring {
            std::ptrdiff_t const idx = center + offset;
            if (idx < 0 || static_cast<size_t>(idx) >= m_lyricLines.size())
            {
                return L"";
            }
            return hstring{ m_lyricLines[static_cast<size_t>(idx)].text };
        };
        std::ptrdiff_t const c = static_cast<std::ptrdiff_t>(cur);
        PlayerLyricLineM2().Text(lineAt(-2, c));
        PlayerLyricLineM1().Text(lineAt(-1, c));
        PlayerLyricLine0().Text(lineAt(0, c));
        PlayerLyricLineP1().Text(lineAt(1, c));
        PlayerLyricLineP2().Text(lineAt(2, c));
    }

    void MainWindow::OnPlayUiTick(IInspectable const&, IInspectable const&)
    {
        if (!m_player)
        {
            return;
        }
        try
        {
            MediaPlaybackSession const s = m_player.PlaybackSession();
            int64_t const posMs = TimeSpanToMs(s.Position());
            int64_t durMs = TimeSpanToMs(s.NaturalDuration());
            if (durMs < 0)
            {
                durMs = 0;
            }
            if (!m_seekDragging && durMs > 0)
            {
                m_suppressSeekSlider = true;
                double const mx = static_cast<double>((std::max)(int64_t{ 500 }, durMs));
                PlayerSeekSlider().Maximum(mx);
                PlayerSeekSlider().Value(static_cast<double>((std::min)(posMs, durMs)));
                m_suppressSeekSlider = false;
            }
            PlayerTimeText().Text(FormatDurationMs(posMs) + hstring{ L" / " } + FormatDurationMs(durMs));
            UpdateLyricUiForPositionMs(posMs);
        }
        catch (...)
        {
        }
    }

    void MainWindow::UpdatePlaybackUiFromSession()
    {
        if (!m_player)
        {
            return;
        }
        try
        {
            MediaPlaybackSession const s = m_player.PlaybackSession();
            MediaPlaybackState const st = s.PlaybackState();
            bool const busy = (st == MediaPlaybackState::Opening) || (st == MediaPlaybackState::Buffering);
            PlayerBufferingRing().Visibility(busy ? Visibility::Visible : Visibility::Collapsed);
            PlayerBufferingRing().IsActive(busy);
            UpdatePlayPauseGlyph(st == MediaPlaybackState::Playing);
        }
        catch (...)
        {
        }
    }

    void MainWindow::OnSessionPlaybackStateChanged(MediaPlaybackSession const&, IInspectable const&)
    {
        DispatcherQueue().TryEnqueue(DispatcherQueuePriority::Normal, [this]() { UpdatePlaybackUiFromSession(); });
    }

    void MainWindow::OnSessionNaturalDurationChanged(MediaPlaybackSession const&, IInspectable const&)
    {
        DispatcherQueue().TryEnqueue(DispatcherQueuePriority::Normal, [this]() { UpdatePlaybackUiFromSession(); });
    }

    void MainWindow::OnPlayerMediaEnded(MediaPlayer const&, IInspectable const&)
    {
        DispatcherQueue().TryEnqueue(DispatcherQueuePriority::Normal, [this]() { HandleTrackEnded(); });
    }

    void MainWindow::OnPlayerMediaFailed(MediaPlayer const&, MediaPlayerFailedEventArgs const& args)
    {
        hstring const msg = args.ErrorMessage();
        DispatcherQueue().TryEnqueue(
            DispatcherQueuePriority::Normal,
            [this, msg]()
            {
                PlayerErrorText().Text(msg.empty() ? L"播放失败" : msg);
                PlayerErrorText().Visibility(Visibility::Visible);
                PlayerBufferingRing().Visibility(Visibility::Collapsed);
                PlayerBufferingRing().IsActive(false);
                UpdatePlayPauseGlyph(false);
            });
    }

    void MainWindow::HandleTrackEnded()
    {
        if (m_playbackQueue.empty() || m_queueIndex < 0)
        {
            return;
        }
        int32_t const i = m_queueIndex;
        int32_t const last = static_cast<int32_t>(m_playbackQueue.size()) - 1;
        switch (m_playbackMode)
        {
        case LibraryPlaybackMode::Order:
            if (i < last)
            {
                BeginPlaybackAt(static_cast<uint32_t>(i + 1));
            }
            else if (m_player)
            {
                m_player.Pause();
                UpdatePlayPauseGlyph(false);
            }
            break;
        case LibraryPlaybackMode::RepeatOne:
            if (m_player)
            {
                m_player.PlaybackSession().Position(std::chrono::milliseconds{ 0 });
                m_player.Play();
                UpdatePlayPauseGlyph(true);
            }
            break;
        case LibraryPlaybackMode::Shuffle:
        {
            int32_t const n = PickShuffleIndex(i);
            BeginPlaybackAt(static_cast<uint32_t>(n));
            break;
        }
        }
    }

    int32_t MainWindow::PickShuffleIndex(int32_t const currentIndex)
    {
        int32_t const n = static_cast<int32_t>(m_playbackQueue.size());
        if (n <= 1)
        {
            return currentIndex;
        }
        std::uniform_int_distribution<int32_t> dist(0, n - 1);
        for (int k = 0; k < 32; ++k)
        {
            int32_t const j = dist(m_shuffleRng);
            if (j != currentIndex)
            {
                return j;
            }
        }
        return (currentIndex + 1) % n;
    }

    void MainWindow::SkipToNext()
    {
        if (m_playbackQueue.empty() || m_queueIndex < 0)
        {
            return;
        }
        int32_t const i = m_queueIndex;
        int32_t const last = static_cast<int32_t>(m_playbackQueue.size()) - 1;
        switch (m_playbackMode)
        {
        case LibraryPlaybackMode::Order:
            if (i < last)
            {
                BeginPlaybackAt(static_cast<uint32_t>(i + 1));
            }
            break;
        case LibraryPlaybackMode::RepeatOne:
            if (m_player)
            {
                m_player.PlaybackSession().Position(std::chrono::milliseconds{ 0 });
                m_player.Play();
                UpdatePlayPauseGlyph(true);
            }
            break;
        case LibraryPlaybackMode::Shuffle:
            BeginPlaybackAt(static_cast<uint32_t>(PickShuffleIndex(i)));
            break;
        }
    }

    void MainWindow::SkipToPrevious()
    {
        if (m_playbackQueue.empty() || m_queueIndex < 0)
        {
            return;
        }
        int32_t const i = m_queueIndex;
        switch (m_playbackMode)
        {
        case LibraryPlaybackMode::Order:
            if (i > 0)
            {
                BeginPlaybackAt(static_cast<uint32_t>(i - 1));
            }
            else if (m_player)
            {
                m_player.PlaybackSession().Position(std::chrono::milliseconds{ 0 });
                m_player.Play();
            }
            break;
        case LibraryPlaybackMode::RepeatOne:
            if (m_player)
            {
                m_player.PlaybackSession().Position(std::chrono::milliseconds{ 0 });
                m_player.Play();
                UpdatePlayPauseGlyph(true);
            }
            break;
        case LibraryPlaybackMode::Shuffle:
            BeginPlaybackAt(static_cast<uint32_t>(PickShuffleIndex(i)));
            break;
        }
    }

    void MainWindow::OnPlayerPlayPauseClick(IInspectable const&, RoutedEventArgs const&)
    {
        EnsureMediaPlayer();
        if (!m_player)
        {
            return;
        }
        try
        {
            MediaPlaybackSession const s = m_player.PlaybackSession();
            MediaPlaybackState const st = s.PlaybackState();
            if (st == MediaPlaybackState::Playing)
            {
                m_player.Pause();
                UpdatePlayPauseGlyph(false);
            }
            else
            {
                if (!m_player.Source())
                {
                    if (m_queueIndex >= 0 && !m_playbackQueue.empty())
                    {
                        BeginPlaybackAt(static_cast<uint32_t>(m_queueIndex));
                    }
                }
                else
                {
                    m_player.Play();
                    UpdatePlayPauseGlyph(true);
                }
            }
        }
        catch (...)
        {
        }
    }

    void MainWindow::OnPlayerPrevClick(IInspectable const&, RoutedEventArgs const&)
    {
        SkipToPrevious();
    }

    void MainWindow::OnPlayerNextClick(IInspectable const&, RoutedEventArgs const&)
    {
        SkipToNext();
    }

    void MainWindow::OnPlayerModeClick(IInspectable const&, RoutedEventArgs const&)
    {
        if (m_playbackMode == LibraryPlaybackMode::Order)
        {
            m_playbackMode = LibraryPlaybackMode::RepeatOne;
        }
        else if (m_playbackMode == LibraryPlaybackMode::RepeatOne)
        {
            m_playbackMode = LibraryPlaybackMode::Shuffle;
        }
        else
        {
            m_playbackMode = LibraryPlaybackMode::Order;
        }
        UpdateModeButtonLabel();
    }

    void MainWindow::OnSeekSliderPointerPressed(IInspectable const&, PointerRoutedEventArgs const&)
    {
        m_seekDragging = true;
    }

    void MainWindow::OnSeekSliderPointerReleased(IInspectable const&, PointerRoutedEventArgs const&)
    {
        m_seekDragging = false;
        if (!m_player)
        {
            return;
        }
        try
        {
            MediaPlaybackSession const s = m_player.PlaybackSession();
            if (!s.CanSeek())
            {
                return;
            }
            int64_t const targetMs = static_cast<int64_t>(PlayerSeekSlider().Value());
            s.Position(std::chrono::milliseconds{ targetMs });
        }
        catch (...)
        {
        }
    }

    void MainWindow::OnPlayerVolumeValueChanged(IInspectable const&, RangeBaseValueChangedEventArgs const& e)
    {
        if (m_suppressVolumeEvents || !m_player)
        {
            return;
        }
        try
        {
            m_player.Volume(static_cast<double>(e.NewValue()) / 100.0);
        }
        catch (...)
        {
        }
    }

    void MainWindow::OnGuestLoginClick(IInspectable const&, RoutedEventArgs const&)
    {
        GuestLoginAsync();
    }

    IAsyncAction MainWindow::GuestLoginAsync()
    {
        auto const life = get_strong();
        HideLoginError();
        bool guestFailed = false;
        try
        {
            co_await winrt::resume_background();
            NcmClient client{ AppSettings::ApiBaseUrl() };
            JsonObject j = co_await client.GetAsync(L"/register/anonimous", {});
            co_await wil::resume_foreground(DispatcherQueue());

            if (NcmJson::ApiCode(j) != 200)
            {
                ShowLoginError(ApiMessage(j));
                co_return;
            }
            auto const ck = NcmJson::ExtractCookie(j);
            if (!ck)
            {
                ShowLoginError(L"未返回游客 cookie");
                co_return;
            }
            SessionData sd;
            sd.Cookie = *ck;
            sd.DisplayLabel = L"游客";
            sd.IsGuest = true;
            co_await SessionStore::SaveAsync(sd);
            co_await wil::resume_foreground(DispatcherQueue());
            ShowMainUi(sd);
        }
        catch (...)
        {
            guestFailed = true;
        }

        if (guestFailed)
        {
            co_await wil::resume_foreground(DispatcherQueue());
            ShowLoginError(L"游客登录失败");
        }
    }

    void MainWindow::OnPhoneLoginClick(IInspectable const&, RoutedEventArgs const&)
    {
        PhoneLoginAsync();
    }

    IAsyncAction MainWindow::PhoneLoginAsync()
    {
        auto const life = get_strong();
        HideLoginError();

        hstring const phone = TrimWs(PhoneBox().Text());
        hstring const captcha = TrimWs(CaptchaBox().Text());
        hstring const pwd = PasswordBox().Password();
        if (phone.empty())
        {
            ShowLoginError(L"请输入手机号");
            co_return;
        }
        if (captcha.empty() && pwd.empty())
        {
            ShowLoginError(L"请输入短信验证码或密码");
            co_return;
        }
        if (!captcha.empty() && !pwd.empty())
        {
            ShowLoginError(L"请只使用验证码或密码其中一种方式登录");
            co_return;
        }

        bool phoneFailed = false;
        try
        {
            std::map<std::wstring, std::wstring> form{
                { L"phone", std::wstring{ phone.c_str() } },
                { L"countrycode", L"86" },
            };
            if (!captcha.empty())
            {
                form.insert({ L"captcha", std::wstring{ captcha.c_str() } });
            }
            else
            {
                hstring const md5 = Md5HexUtf8String(pwd);
                form.insert({ L"md5_password", std::wstring{ md5.c_str() } });
            }

            co_await winrt::resume_background();
            NcmClient client{ AppSettings::ApiBaseUrl() };
            JsonObject j = co_await client.PostFormAsync(L"/login/cellphone", form);
            co_await wil::resume_foreground(DispatcherQueue());

            if (NcmJson::ApiCode(j) != 200)
            {
                ShowLoginError(ApiMessage(j));
                co_return;
            }
            auto const ck = NcmJson::ExtractCookie(j);
            if (!ck)
            {
                ShowLoginError(L"登录成功但未返回 cookie");
                co_return;
            }
            SessionData sd;
            sd.Cookie = *ck;
            sd.DisplayLabel = NcmJson::DisplayLabelFromLogin(j).value_or(L"");
            sd.IsGuest = false;
            co_await SessionStore::SaveAsync(sd);
            co_await wil::resume_foreground(DispatcherQueue());
            ShowMainUi(sd);
        }
        catch (...)
        {
            phoneFailed = true;
        }

        if (phoneFailed)
        {
            co_await wil::resume_foreground(DispatcherQueue());
            ShowLoginError(L"手机号登录失败");
        }
    }

    void MainWindow::OnLogoutClick(IInspectable const&, RoutedEventArgs const&)
    {
        LogoutAsync();
    }

    IAsyncAction MainWindow::LogoutAsync()
    {
        auto const life = get_strong();
        co_await SessionStore::ClearAsync();
        co_await wil::resume_foreground(DispatcherQueue());
        PasswordBox().Password(L"");
        CaptchaBox().Text(L"");
        ShowLoginUi(true);
    }
}
