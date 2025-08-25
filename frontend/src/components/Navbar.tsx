import React from "react";
import { useAuth } from "../auth/AuthProvider";

const buttonBase =
  "inline-flex items-center justify-center rounded-lg px-4 py-2 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2";
const buttonPrimary =
  `${buttonBase} bg-blue-600 hover:bg-blue-700 text-white focus:ring-blue-500`;
const buttonDestructive =
  `${buttonBase} bg-red-600 hover:bg-red-700 text-white focus:ring-red-500`;

export const Navbar: React.FC = () => {
  const { authenticated, user, login, logout } = useAuth();

  const displayName =
    user?.username ??
    [user?.firstName, user?.lastName].filter(Boolean).join(" ");

  return (
    <nav className="sticky top-0 z-40 w-full bg-gray-900 text-white shadow-md">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3">
        <span className="text-lg font-semibold tracking-wide">
          Dead Letter Explorer
        </span>

        {authenticated ? (
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-300">{displayName}</span>
            <button
              className={buttonDestructive}
              onClick={() => logout({ redirectUri: window.location.origin })}
            >
              Logout
            </button>
          </div>
        ) : (
          <button className={buttonPrimary} onClick={() => login()}>
            Login
          </button>
        )}
      </div>
    </nav>
  );
};
