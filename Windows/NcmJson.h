#pragma once

#include "pch.h"

namespace winrt::ZMusic
{
    struct NcmJson
    {
        static int32_t ApiCode(Windows::Data::Json::JsonObject const& json);

        static std::optional<hstring> ExtractCookie(Windows::Data::Json::JsonObject const& json);

        /**
         * `/login/status` 正文是否表示已登录（code 须为 200）。
         * 兼容嵌套 data、根级 profile/account；profile.userId 亦可作为已登录依据。
         * 游客会话应在调用方跳过本接口；若传入 isGuestSession=true 则仅在 code==200 时返回 true。
         */
        static bool IsLoggedInStatus(Windows::Data::Json::JsonObject const& json, bool isGuestSession);

        static std::optional<hstring> DisplayLabelFromLogin(Windows::Data::Json::JsonObject const& json);

        static std::optional<hstring> QrImgBase64(Windows::Data::Json::JsonObject const& json);

        static std::optional<hstring> QrKey(Windows::Data::Json::JsonObject const& json);

        static int32_t QrCheckCode(Windows::Data::Json::JsonObject const& json);

        /** 从 `/login/status` 解析用户 id，供 `/user/playlist` 等接口使用。 */
        static std::optional<int64_t> UserIdFromLoginStatus(Windows::Data::Json::JsonObject const& json);
    };
}
