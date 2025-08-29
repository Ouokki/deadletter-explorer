import React from "react";
import { useAuth } from "../auth/AuthProvider";
import { NavLink, useNavigate } from "react-router-dom";

const cx = (...cls: Array<string | false | null | undefined>) =>
  cls.filter(Boolean).join(" ");

export const Navbar: React.FC = () => {
  const { authenticated, user, login, logout } = useAuth();
  const navigate = useNavigate();
  const [topic, setTopic] = React.useState<string>("orders-DLQ");
  const [open, setOpen] = React.useState(false);

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
    setOpen(false);
  };


  return (
    <nav className="sticky top-0 z-40 w-full border-b border-white/10 bg-[#0C1222] text-slate-100">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3 md:px-6">
        <div className="flex items-center gap-3">
          <NavLink
            to="/home"
            className="text-sm font-semibold tracking-tight"
            end
          >
            Dead Letter Explorer
          </NavLink>

          {authenticated && (
            <div className="ml-2 hidden items-center gap-1 md:flex">
              <NavLink
                to="/home"
                end
                className={({ isActive }) =>
                  `rounded-md px-3 py-2 text-sm font-medium ${
                    isActive
                      ? "text-white bg-white/10"
                      : "text-slate-300 hover:text-white hover:bg-white/5"
                  }`
                }
              >
                Home
              </NavLink>
            </div>
          )}
        </div>

        <div className="hidden items-center gap-3 md:flex">
          {authenticated ? (
            <>
              <span className="text-xs text-slate-300">{displayName}</span>
              <button
                className="inline-flex items-center justify-center rounded-lg px-3.5 py-2 text-sm font-medium bg-indigo-600 hover:bg-indigo-500 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 focus:ring-offset-[#0C1222]"
                onClick={() => logout({ redirectUri: window.location.origin })}
              >
                Logout
              </button>
            </>
          ) : (
            <button
              className="inline-flex items-center justify-center rounded-lg px-3.5 py-2 text-sm font-medium bg-indigo-600 hover:bg-indigo-500 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 focus:ring-offset-[#0C1222]"
              onClick={() => login()}
            >
              Login
            </button>
          )}
        </div>
      </div>
    </nav>

  );
};
