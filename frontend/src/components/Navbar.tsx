import React from "react";
import { useAuth } from "../auth/AuthProvider";
import { NavLink, useNavigate } from "react-router-dom";

const buttonBase =
  "inline-flex items-center justify-center rounded-lg px-4 py-2 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2";
const buttonPrimary =
  `${buttonBase} bg-blue-600 hover:bg-blue-700 text-white focus:ring-blue-500`;
const buttonDestructive =
  `${buttonBase} bg-red-600 hover:bg-red-700 text-white focus:ring-red-500`;

const linkBase = "rounded-md px-3 py-2 text-sm font-medium";
const linkIdle = "text-gray-300 hover:bg-gray-800 hover:text-white";
const linkActive = "bg-gray-800 text-white";

export const Navbar: React.FC = () => {
  const { authenticated, user, login, logout } = useAuth();
  const navigate = useNavigate();
  const [topic, setTopic] = React.useState<string>("orders-DLQ");

  // keep last topic between refreshes (optional)
  React.useEffect(() => {
    const t = localStorage.getItem("lastTopic");
    if (t) setTopic(t);
  }, []);
  React.useEffect(() => {
    if (topic) localStorage.setItem("lastTopic", topic);
  }, [topic]);

  const displayName =
    user?.username ??
    [user?.firstName, user?.lastName].filter(Boolean).join(" ");

  const goToStudio: React.FormEventHandler<HTMLFormElement> = (e) => {
    e.preventDefault();
    const t = topic.trim();
    if (!t) return;
    navigate(`/topics/${encodeURIComponent(t)}/redaction`);
  };

  return (
    <nav className="sticky top-0 z-40 w-full bg-gray-900 text-white shadow-md">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3">
        {/* Brand + left nav */}
        <div className="flex items-center gap-4">
          <NavLink
            to="/home"
            className={({ isActive }) =>
              `${linkBase} ${isActive ? linkActive : linkIdle}`
            }
            end
          >
            Dead Letter Explorer
          </NavLink>

          {authenticated && (
            <div className="hidden items-center gap-1 sm:flex">
              <NavLink
                to="/home"
                end
                className={({ isActive }) =>
                  `${linkBase} ${isActive ? linkActive : linkIdle}`
                }
              >
                Home
              </NavLink>

              <form onSubmit={goToStudio} className="ml-2 flex items-center gap-2">
                <input
                  value={topic}
                  onChange={(e) => setTopic(e.target.value)}
                  placeholder="topic (e.g. orders-DLQ)"
                  className="w-48 rounded-md border border-gray-700 bg-gray-800 px-3 py-1.5 text-sm text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <button
                  type="submit"
                  className={`${buttonBase} bg-gray-800 hover:bg-gray-700 text-white focus:ring-gray-600`}
                  title="Open Redaction Studio for this topic"
                >
                  Redaction Studio
                </button>
              </form>
            </div>
          )}
        </div>

        {/* Right side: auth */}
        {authenticated ? (
          <div className="flex items-center gap-4">
            <span className="hidden text-sm text-gray-300 sm:inline">
              {displayName}
            </span>
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
