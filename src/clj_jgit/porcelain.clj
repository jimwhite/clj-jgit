(ns clj-jgit.porcelain
  (:require [clojure.java.io :as io]
            [clj-file-utils.core :as file]
            [clj-jgit.util.core :as util])
  (:import [java.io.FileNotFoundException]
           [org.eclipse.jgit.lib RepositoryBuilder]
           [org.eclipse.jgit.api
            Git
            InitCommand StatusCommand AddCommand
            ListBranchCommand PullCommand]))

(defn load-repo
  "Given a path (either to the parent folder or to the `.git` folder itself), load the Git repository"
  [path]
  (let [dir (if (not (re-find #"\.git$" path))
              (io/as-file (str path "/.git"))
              (io/as-file path))]
    (if (.exists dir)
      (let [repo (-> (RepositoryBuilder.)
                     (.setGitDir dir)
                     (.readEnvironment)
                     (.findGitDir)
                     (.build))]
        (Git. repo))
      (throw (java.io.FileNotFoundException.
              (str "The Git repository at '" path "' could not be located."))))))

(defn git-add
  "The `file-pattern` is either a single file name (exact, not a pattern) or the name of a directory. If a directory is supplied, all files within that directory will be added. If `only-update?` is set to `true`, only files which are already part of the index will have their changes staged (i.e. no previously untracked files will be added to the index)."
  ([repo file-pattern] (git-add repo file-pattern false))
  ([repo file-pattern only-update?]
     (-> repo
         (.add)
         (.addFilepattern file-pattern)
         (.setUpdate only-update?)
         (.call))))

(defn git-branch-list
  "Get a list of branches in the Git repo. Return the default objects generated by the JGit API."
  ([repo] (git-branch-list repo :local))
  ([repo opt]
     (let [opt-val {:all org.eclipse.jgit.api.ListBranchCommand$ListMode/ALL
                    :remote org.eclipse.jgit.api.ListBranchCommand$ListMode/REMOTE}
           branches (if (= opt :local)
                      (-> repo
                          (.branchList)
                          (.call))
                      (-> repo
                          (.branchList)
                          (.setListMode (opt opt-val))
                          (.call)))]
       (seq branches))))

(defn git-branch-create
  "Create a new branch in the Git repository."
  ([repo branch-name]        (git-branch-create repo branch-name false nil))
  ([repo branch-name force?] (git-branch-create repo branch-name force? nil))
  ([repo branch-name force? start-point]
     (if (nil? start-point)
       (-> repo
           (.branchCreate)
           (.setName branch-name)
           (.setForce force?)
           (.call))
       (-> repo
           (.branchCreate)
           (.setName branch-name)
           (.setForce force?)
           (.setStartPoint start-point)
           (.call)))))

(defn git-branch-delete
  ([repo branch-names] (git-branch-delete repo branch-names false))
  ([repo branch-names force?]
     (-> repo
         (.branchDelete)
         (.setBranchNames (into-array String branch-names))
         (.setForce force?)
         (.call))))

(defn git-checkout
  ([repo branch-name] (git-checkout repo branch-name false false nil))
  ([repo branch-name create-branch?] (git-checkout repo branch-name create-branch? false nil))
  ([repo branch-name create-branch? force?] (git-checkout repo branch-name create-branch? force? nil))
  ([repo branch-name create-branch? force? start-point]
     (if (nil? start-point)
       (-> repo
           (.checkout)
           (.setName branch-name)
           (.setCreateBranch create-branch?)
           (.setForce force?)
           (.call))
       (-> repo
           (.checkout)
           (.setName branch-name)
           (.setCreateBranch create-branch?)
           (.setForce force?)
           (.setStartPoint start-point)
           (.call)))))

(defn git-cherry-pick [])

(defn git-clone
  ([uri] (git-clone uri (util/name-from-uri uri) "master" "master" false))
  ([uri local-dir] (git-clone uri local-dir "master" "master" false))
  ([uri local-dir remote-branch] (git-clone uri local-dir remote-branch "master" false))
  ([uri local-dir remote-branch local-branch] (git-clone uri local-dir remote-branch local-branch false))
  ([uri local-dir remote-branch local-branch bare?]
     (-> (Git/cloneRepository)
         (.setURI uri)
         (.setDirectory (io/as-file local-dir))
         (.setRemote remote-branch)
         (.setBranch local-branch)
         (.setBare bare?)
         (.call))))

(declare git-fetch)
(declare git-merge)
(defn git-clone-full
  "Clone, fetch the master branch and merge its latest commit"
  ([uri] (git-clone-full uri (util/name-from-uri uri) "master" "master" false))
  ([uri local-dir] (git-clone-full uri local-dir "master" "master" false))
  ([uri local-dir remote-branch] (git-clone-full uri local-dir remote-branch "master" false))
  ([uri local-dir remote-branch local-branch] (git-clone-full uri local-dir remote-branch local-branch false))
  ([uri local-dir remote-branch local-branch bare?]
     (let [new-repo (-> (Git/cloneRepository)
                        (.setURI uri)
                        (.setDirectory (io/as-file local-dir))
                        (.setRemote remote-branch)
                        (.setBranch local-branch)
                        (.setBare bare?)
                        (.call))
           fetch-result (git-fetch new-repo)
           merge-result (git-merge new-repo
                                   (first (.getAdvertisedRefs fetch-result)))]
       {:repo new-repo,
        :fetch-result fetch-result,
        :merge-result  merge-result})))

(defn git-commit
  ([repo message]
     (-> repo
         (.commit)
         (.setMessage message)
         (.call)))
  ([repo message {:keys [name email]}]
     (-> repo
         (.commit)
         (.setMessage message)
         (.setAuthor name email)
         (.setCommitter name email)
         (.call)))
  ([repo message {:keys [author-name author-email]} {:keys [committer-name committer-email]}]
     (-> repo
         (.commit)
         (.setMessage message)
         (.setAuthor author-name author-email)
         (.setCommitter committer-name committer-email)
         (.call))))

(defn git-commit-amend
  ([repo message]
     (-> repo
         (.commit)
         (.setMessage message)
         (.setAmend true)
         (.call)))
  ([repo message {:keys [name email]}]
     (-> repo
         (.commit)
         (.setMessage message)
         (.setAuthor name email)
         (.setAmend true)
         (.call)))
  ([repo message {:keys [name email]} {:keys [name email]}]
     (-> repo
         (.commit)
         (.setMessage message)
         (.setAuthor name email)
         (.setCommitter name email)
         (.setAmend true)
         (.call))))


(defn git-add-and-commit
  "This is the `git commit -a...` command"
  ([repo message]
     (-> repo
         (.commit)
         (.setMessage message)
         (.setAll true)
         (.call)))
  ([repo message {:keys [name email]}]
     (-> repo
         (.commit)
         (.setMessage message)
         (.setAuthor name email)
         (.setAll true)
         (.call)))
  ([repo message {:keys [name email]} {:keys [name email]}]
     (-> repo
         (.commit)
         (.setMessage message)
         (.setAuthor name email)
         (.setCommitter name email)
         (.setAll true)
         (.call))))

(defn git-fetch
  ([repo]
     (-> repo
         (.fetch)
         (.setRemote "master")
         (.call)))
  ([repo remote]
     (-> repo
         (.fetch)
         (.setRemote remote)
         (.call))))

(defn git-init
  "Initialize and load a new Git repository"
  ([] (git-init "."))
  ([target-dir]
     (let [comm (InitCommand.)]
       (-> comm
           (.setDirectory (io/as-file target-dir))
           (.call)))))

(defn git-log
  "Return a seq of all commit objects"
  [repo]
  (seq (-> repo
           (.log)
           (.call))))

(defn git-merge
  [repo commit-ref]
  (-> repo
      (.merge)
      (.include commit-ref)
      (.call)))

(defn git-pull
  "NOT WORKING: Requires work with configuration"
  [repo]
  (-> repo
      (.pull)
      (.call)))

(defn git-push [])
(defn git-rebase [])
(defn git-revert [])
(defn git-rm
  [repo file-pattern]
  (-> repo
      (.rm)
      (.addFilepattern file-pattern)
      (.call)))

(defn git-status
  "Return the status of the Git repository. Opts will return individual aspects of the status, and can be specified as `:added`, `:changed`, `:missing`, `:modified`, `:removed`, or `:untracked`."
  [repo & fields]
  (let [status (.. repo status call)
        status-fns {:added     #(.getAdded %)
                    :changed   #(.getChanged %)
                    :missing   #(.getMissing %)
                    :modified  #(.getModified %)
                    :removed   #(.getRemoved %)
                    :untracked #(.getUntracked %)}]
    (if-not (seq fields)
      (apply merge (for [[k f] status-fns]
                     {k (into #{} (f status))}))
      (apply merge (for [field fields]
                     {field (into #{} ((field status-fns) status))})))))

(defn git-tag [])