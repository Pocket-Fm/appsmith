import React from "react";
import type { AdminConfigType } from "ee/pages/AdminSettings/config/types";
import {
  CategoryType,
  SettingCategories,
  SettingTypes,
} from "ee/pages/AdminSettings/config/types";

/**
 * PocketFM CE fork: Simple Access Control page that explains where to manage
 * workspace-level roles, replacing the EE upgrade prompt.
 */
function AccessControlInfoPage() {
  return React.createElement(
    "div",
    { style: { padding: "24px 32px", maxWidth: 720 } },
    React.createElement("h2", null, "Access Control"),
    React.createElement(
      "p",
      { style: { color: "#6b7280", marginBottom: 16 } },
      "Granular Access Control (GAC) is enabled. Permission enforcement is active on the backend.",
    ),
    React.createElement(
      "p",
      { style: { marginBottom: 8 } },
      "Each workspace has three built-in roles:",
    ),
    React.createElement(
      "ul",
      { style: { marginLeft: 20, marginBottom: 16 } },
      React.createElement(
        "li",
        null,
        React.createElement("strong", null, "Administrator"),
        " — Full control over the workspace, apps, and datasources",
      ),
      React.createElement(
        "li",
        null,
        React.createElement("strong", null, "Developer"),
        " — Can edit apps and queries, but cannot manage workspace settings",
      ),
      React.createElement(
        "li",
        null,
        React.createElement("strong", null, "App Viewer"),
        " — Can only view published apps",
      ),
    ),
    React.createElement(
      "p",
      { style: { color: "#6b7280" } },
      "To manage members and roles, go to Workspace Settings → Members.",
    ),
  );
}

export const config: AdminConfigType = {
  icon: "user-3-line",
  type: SettingCategories.ACCESS_CONTROL,
  categoryType: CategoryType.USER_MANAGEMENT,
  controlType: SettingTypes.PAGE,
  component: AccessControlInfoPage,
  title: "Access Control",
  canSave: false,
  isFeatureEnabled: true,
} as AdminConfigType;
