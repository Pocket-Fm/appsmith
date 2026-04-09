import {
  GoogleOAuthURL,
  GithubOAuthURL,
  OAuthURL,
} from "ee/constants/ApiConstants";

import GithubLogo from "assets/images/Github.png";
import GoogleLogo from "assets/images/Google.png";
import OidcLogo from "assets/images/oidc.svg";
export interface SocialLoginButtonProps {
  url: string;
  name: string;
  logo: string;
  label?: string;
}

export const GoogleSocialLoginButtonProps: SocialLoginButtonProps = {
  url: GoogleOAuthURL,
  name: "Google",
  logo: GoogleLogo,
};

export const GithubSocialLoginButtonProps: SocialLoginButtonProps = {
  url: GithubOAuthURL,
  name: "Github",
  logo: GithubLogo,
};

// PocketFM CE fork: OIDC SSO (JumpCloud / generic OIDC)
export const OidcSocialLoginButtonProps: SocialLoginButtonProps = {
  url: `${OAuthURL}/oidc`,
  name: "OIDC SSO",
  logo: OidcLogo,
  label: "Sign in with OIDC",
};

export const SocialLoginButtonPropsList: Record<
  string,
  SocialLoginButtonProps
> = {
  google: GoogleSocialLoginButtonProps,
  github: GithubSocialLoginButtonProps,
  oidc: OidcSocialLoginButtonProps,
};

export type SocialLoginType = keyof typeof SocialLoginButtonPropsList;
